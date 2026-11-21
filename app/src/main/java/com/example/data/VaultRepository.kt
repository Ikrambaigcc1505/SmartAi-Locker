package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class VaultRepository(
    private val context: Context,
    private val dao: VaultDao,
    private val prefs: SecurityPreferences
) {

    private val encryptedVaultDir: File
        get() = File(context.filesDir, "vault_encrypted").apply { mkdirs() }

    private val intruderDir: File
        get() = File(context.filesDir, "intruder_encrypted").apply { mkdirs() }

    private val streamCacheDir: File
        get() = File(context.cacheDir, "vault_stream").apply { mkdirs() }

    private val backupJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeActiveItems(): Flow<List<VaultItemEntity>> = dao.observeActiveItems()

    fun observeCategoryItems(category: VaultCategory): Flow<List<VaultItemEntity>> =
        dao.observeCategoryItems(category.name)

    fun observeTrashItems(): Flow<List<VaultItemEntity>> = dao.observeTrashItems()

    fun observeLockedApps(): Flow<List<LockedAppEntity>> = dao.observeLockedApps()

    fun observeIntruderLogs(): Flow<List<IntruderLogEntity>> = dao.observeIntruderLogs()

    suspend fun importFromUri(
        uri: Uri,
        targetCategory: VaultCategory? = null
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        runCatching {
            var displayName = "vault_${System.currentTimeMillis()}"
            var reportedSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            val mimeType = context.contentResolver.getType(uri)
                ?: SmartAiClassifier.guessMimeFromName(displayName)

            val id = UUID.randomUUID().toString()
            val encFileName = "$id.enc"
            val destFile = File(encryptedVaultDir, encFileName)

            var snippet: String? = null
            val streamedSize = context.contentResolver.openInputStream(uri)?.use { rawIn ->
                val bis = BufferedInputStream(rawIn, 64 * 1024)
                if (mimeType.startsWith("text/") || mimeType == "application/pdf") {
                    bis.mark(4096)
                    val probe = ByteArray(4096)
                    val probeLen = bis.read(probe)
                    if (probeLen > 0) {
                        snippet = String(probe, 0, probeLen, Charsets.UTF_8)
                    }
                    bis.reset()
                }
                VaultCryptoManager.encryptStreamToFile(bis, destFile)
            } ?: error("Unable to read selected file.")

            val finalSize = if (streamedSize > 0L) streamedSize else reportedSize
            val aiResult = SmartAiClassifier.classifyFileMetadata(
                displayName = displayName,
                mimeType = mimeType,
                snippetText = snippet,
                sizeBytes = finalSize
            )
            val resolvedCategory = targetCategory ?: aiResult.recommendedCategory

            val folderHint = when (resolvedCategory) {
                VaultCategory.PHOTOS -> "Pictures/SmartAI"
                VaultCategory.VIDEOS -> "Movies/SmartAI"
                VaultCategory.AUDIO -> "Music/SmartAI"
                VaultCategory.DOCUMENTS -> "Downloads/SmartAI"
            }

            val entity = VaultItemEntity(
                id = id,
                category = resolvedCategory.name,
                originalName = displayName,
                mimeType = mimeType,
                sizeBytes = finalSize,
                encryptedFileName = encFileName,
                originalFolderHint = folderHint,
                aiCategoryTag = aiResult.aiTag,
                aiConfidence = aiResult.confidence,
                isSoftDeleted = false
            )
            dao.upsertItem(entity)
            entity
        }
    }

    suspend fun readDecryptedBytes(item: VaultItemEntity): ByteArray = withContext(Dispatchers.IO) {
        val file = File(encryptedVaultDir, item.encryptedFileName)
        require(file.exists()) { "Encrypted vault payload is missing." }
        VaultCryptoManager.decryptFromFile(file)
    }

    /**
     * Streams decrypted bytes in 64 KB AES-GCM chunks directly into a temp playback file
     * so 4K videos and large PDFs never load whole-file byte arrays into RAM.
     */
    suspend fun createTempStreamFile(item: VaultItemEntity): File = withContext(Dispatchers.IO) {
        val encFile = File(encryptedVaultDir, item.encryptedFileName)
        require(encFile.exists()) { "Encrypted vault payload is missing." }
        val ext = item.originalName.substringAfterLast('.', "tmp")
        val tempFile = File(streamCacheDir, "${item.id}.$ext")
        FileOutputStream(tempFile).use { fos ->
            BufferedOutputStream(fos, 64 * 1024).use { bos ->
                VaultCryptoManager.decryptFileToStream(encFile, bos)
            }
        }
        tempFile
    }

    fun clearTempStreamCache() {
        streamCacheDir.listFiles()?.forEach { VaultCryptoManager.secureDeleteFile(it) }
    }

    suspend fun unhideAndRestoreToDevice(item: VaultItemEntity): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encFile = File(encryptedVaultDir, item.encryptedFileName)
                require(encFile.exists()) { "Encrypted vault payload is missing." }

                val category = VaultCategory.entries.firstOrNull { it.name == item.category }
                    ?: VaultCategory.DOCUMENTS

                val restoredLocationLabel = restoreStreamViaMediaStoreOrFallback(
                    category = category,
                    displayName = item.originalName,
                    mimeType = item.mimeType
                ) { outStream ->
                    VaultCryptoManager.decryptFileToStream(encFile, outStream)
                }

                VaultCryptoManager.secureDeleteFile(encFile)
                dao.deleteItemPermanently(item.id)
                restoredLocationLabel
            }
        }

    private fun restoreStreamViaMediaStoreOrFallback(
        category: VaultCategory,
        displayName: String,
        mimeType: String,
        streamWriter: (OutputStream) -> Unit
    ): String {
        val (collectionUri, publicDirType, relativeFolder) = when (category) {
            VaultCategory.PHOTOS -> Triple(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_PICTURES,
                "${Environment.DIRECTORY_PICTURES}/SmartAI"
            )
            VaultCategory.VIDEOS -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_MOVIES,
                "${Environment.DIRECTORY_MOVIES}/SmartAI"
            )
            VaultCategory.AUDIO -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_MUSIC,
                "${Environment.DIRECTORY_MUSIC}/SmartAI"
            )
            VaultCategory.DOCUMENTS -> Triple(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_DOWNLOADS,
                "${Environment.DIRECTORY_DOWNLOADS}/SmartAI"
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val primaryAttempt = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val targetUri = resolver.insert(collectionUri, values)
                    ?: error("MediaStore primary collection returned null")
                try {
                    resolver.openOutputStream(targetUri)?.use { out ->
                        streamWriter(out)
                    } ?: error("Could not open MediaStore output stream.")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(targetUri, values, null, null)
                } catch (t: Throwable) {
                    runCatching { resolver.delete(targetUri, null, null) }
                    throw t
                }
                relativeFolder
            }
            if (primaryAttempt.isSuccess) return primaryAttempt.getOrThrow()

            // Fallback to MediaStore.Downloads / SmartAI if category MIME mismatched MediaStore validation
            val downloadsFallback = "${Environment.DIRECTORY_DOWNLOADS}/SmartAI"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, downloadsFallback)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val fallbackUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not restore file to device storage.")
            resolver.openOutputStream(fallbackUri)?.use { out ->
                streamWriter(out)
            } ?: error("Could not write restored file stream.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(fallbackUri, values, null, null)
            return downloadsFallback
        } else {
            @Suppress("DEPRECATION")
            val baseDir = Environment.getExternalStoragePublicDirectory(publicDirType)
            val outDir = File(baseDir, "SmartAI").apply { mkdirs() }
            val outFile = File(outDir, displayName)
            FileOutputStream(outFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    streamWriter(bos)
                }
            }
            return "$publicDirType/SmartAI"
        }
    }

    suspend fun softDeleteItem(id: String) = withContext(Dispatchers.IO) {
        dao.softDeleteItem(id)
    }

    suspend fun restoreFromTrash(id: String) = withContext(Dispatchers.IO) {
        dao.restoreFromTrash(id)
    }

    suspend fun permanentDeleteItem(item: VaultItemEntity) = withContext(Dispatchers.IO) {
        VaultCryptoManager.secureDeleteFile(File(encryptedVaultDir, item.encryptedFileName))
        dao.deleteItemPermanently(item.id)
    }

    suspend fun runOnDeviceVaultCategorization(): Int = withContext(Dispatchers.IO) {
        val items = dao.getAllItemsOnce().filter { !it.isSoftDeleted }
        var updated = 0
        for (item in items) {
            val result = SmartAiClassifier.classifyFileMetadata(
                displayName = item.originalName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes
            )
            if (result.aiTag != item.aiCategoryTag || result.confidence != item.aiConfidence) {
                dao.updateAiTag(item.id, result.aiTag, result.confidence)
                updated++
            }
        }
        updated
    }

    suspend fun toggleLockedApp(packageName: String, appName: String, locked: Boolean) =
        withContext(Dispatchers.IO) {
            dao.upsertLockedApp(
                LockedAppEntity(
                    packageName = packageName,
                    appName = appName,
                    isLocked = locked
                )
            )
        }

    suspend fun logIntruderAttempt(
        method: AuthMethod,
        failedCount: Int,
        encryptedPhotoFileName: String?,
        note: String? = null
    ) = withContext(Dispatchers.IO) {
        val base = "Failed ${method.label} attempt ($failedCount/3)"
        val summary = if (!note.isNullOrBlank()) "$base • $note" else base
        val log = IntruderLogEntity(
            attemptMethod = method.label,
            failedAttemptCount = failedCount,
            encryptedPhotoFileName = encryptedPhotoFileName,
            summary = summary
        )
        dao.insertIntruderLog(log)
    }

    suspend fun readIntruderPhotoBytes(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(intruderDir, fileName)
            if (file.exists()) VaultCryptoManager.decryptFromFile(file) else null
        }.getOrNull()
    }

    suspend fun deleteIntruderLog(log: IntruderLogEntity) = withContext(Dispatchers.IO) {
        log.encryptedPhotoFileName?.let {
            VaultCryptoManager.secureDeleteFile(File(intruderDir, it))
        }
        dao.deleteIntruderLog(log.id)
    }

    suspend fun clearAllIntruderLogs() = withContext(Dispatchers.IO) {
        intruderDir.listFiles()?.forEach { VaultCryptoManager.secureDeleteFile(it) }
        dao.clearAllIntruderLogs()
    }

    /**
     * Exports a V2 streaming AES-256-GCM .smartvault archive (ZIP inside ChunkedGcmOutputStream),
     * ensuring large 4K videos never load completely into memory.
     */
    suspend fun exportEncryptedBackupToDownloads(
        passphrase: String,
        customTargetUri: Uri? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(passphrase.length >= 4) { "Passphrase must be at least 4 characters." }
            val items = dao.getAllItemsOnce().filter {
                File(encryptedVaultDir, it.encryptedFileName).exists()
            }
            val manifestItems = items.map { entity ->
                BackupItemMetadataRecord(
                    id = entity.id,
                    categoryName = entity.category,
                    originalName = entity.originalName,
                    mimeType = entity.mimeType,
                    sizeBytes = entity.sizeBytes,
                    originalFolderHint = entity.originalFolderHint,
                    aiCategoryTag = entity.aiCategoryTag,
                    aiConfidence = entity.aiConfidence,
                    isSoftDeleted = entity.isSoftDeleted,
                    deletedAt = entity.deletedAt,
                    importedAt = entity.importedAt,
                    archiveEntryName = "items/${entity.id}.bin"
                )
            }
            val lockedApps = dao.getLockedAppsOnce().map {
                BackupAppLockRecord(it.packageName, it.appName, it.isLocked)
            }
            val cfg = prefs.configFlow.first()
            val backupSettings = BackupSettingsRecord(
                pinLength = cfg.pinLength,
                autoLockSeconds = cfg.autoLockSeconds,
                wifiLocked = cfg.wifiLocked,
                mobileDataLocked = cfg.mobileDataLocked,
                bluetoothLocked = cfg.bluetoothLocked,
                usbLocked = cfg.usbLocked,
                settingsAppLocked = cfg.settingsAppLocked,
                notificationPanelLocked = cfg.notificationPanelLocked,
                keepUnlockedDuringPlayback = cfg.keepUnlockedDuringPlayback,
                backgroundAudioPlay = cfg.backgroundAudioPlay,
                randomizeKeypad = cfg.randomizeKeypad,
                fakeCrashEnabled = cfg.fakeCrashEnabled,
                panicLockEnabled = cfg.panicLockEnabled,
                disguiseModeName = cfg.disguiseMode.name
            )
            val manifest = SmartVaultBackupManifest(
                exportedAt = System.currentTimeMillis(),
                items = manifestItems,
                lockedApps = lockedApps,
                settings = backupSettings
            )
            val manifestBytes = backupJson.encodeToString(manifest).toByteArray(Charsets.UTF_8)

            fun writeArchiveTo(rawOut: OutputStream) {
                VaultCryptoManager.openStreamingBackupOutput(rawOut, passphrase).use { encOut ->
                    ZipOutputStream(BufferedOutputStream(encOut, 64 * 1024)).use { zos ->
                        zos.putNextEntry(ZipEntry("manifest.json"))
                        zos.write(manifestBytes)
                        zos.closeEntry()

                        for (entity in items) {
                            val encFile = File(encryptedVaultDir, entity.encryptedFileName)
                            if (encFile.exists()) {
                                zos.putNextEntry(ZipEntry("items/${entity.id}.bin"))
                                VaultCryptoManager.decryptFileToStream(encFile, zos)
                                zos.closeEntry()
                            }
                        }
                        zos.finish()
                    }
                }
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "SmartAI_Backup_$stamp.smartvault"

            if (customTargetUri != null) {
                context.contentResolver.openOutputStream(customTargetUri)?.use { out ->
                    writeArchiveTo(out)
                } ?: error("Unable to write to chosen location.")
                fileName
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create backup in Downloads folder.")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        writeArchiveTo(out)
                    } ?: error("Failed writing backup stream.")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (t: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw t
                }
                "Downloads/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ).apply { mkdirs() }
                FileOutputStream(File(downloads, fileName)).use { out ->
                    writeArchiveTo(out)
                }
                "Downloads/$fileName"
            }
        }
    }

    /**
     * Imports either a V2 streaming .smartvault backup or a legacy V1 .smartvault package.
     */
    suspend fun importEncryptedBackup(uri: Uri, passphrase: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { rawIn ->
                    when (val handle = VaultCryptoManager.openBackupInput(rawIn, passphrase)) {
                        is VaultCryptoManager.BackupStreamHandle.StreamingV2 -> {
                            importStreamingV2Archive(handle.decryptedStream)
                        }
                        is VaultCryptoManager.BackupStreamHandle.LegacyV1 -> {
                            val plainJsonBytes = VaultCryptoManager.decryptLegacyV1PayloadAfterMagic(
                                handle.magicAlreadyConsumedInStream,
                                passphrase
                            )
                            val payload = backupJson.decodeFromString<SmartVaultBackupPackage>(
                                String(plainJsonBytes, Charsets.UTF_8)
                            )
                            importLegacyV1Payload(payload)
                        }
                    }
                } ?: error("Could not read selected backup file.")
            }
        }

    private suspend fun importStreamingV2Archive(decryptedZipStream: InputStream): Int {
        var manifest: SmartVaultBackupManifest? = null
        val restoredFileIds = mutableSetOf<String>()

        ZipInputStream(BufferedInputStream(decryptedZipStream, 64 * 1024)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "manifest.json" -> {
                        val bos = ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val r = zis.read(buf)
                            if (r < 0) break
                            bos.write(buf, 0, r)
                        }
                        manifest = backupJson.decodeFromString<SmartVaultBackupManifest>(
                            bos.toString(Charsets.UTF_8.name())
                        )
                    }
                    entry.name.startsWith("items/") && entry.name.endsWith(".bin") -> {
                        val itemId = entry.name.removePrefix("items/").removeSuffix(".bin")
                        if (itemId.isNotBlank() && !itemId.contains("/") && !itemId.contains("..")) {
                            val destFile = File(encryptedVaultDir, "$itemId.enc")
                            VaultCryptoManager.encryptStreamToFile(NonClosingInputStream(zis), destFile)
                            restoredFileIds += itemId
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val parsedManifest = manifest ?: error("Backup manifest missing or corrupted.")
        var restoredCount = 0
        for (item in parsedManifest.items) {
            if (restoredFileIds.contains(item.id)) {
                dao.upsertItem(
                    VaultItemEntity(
                        id = item.id,
                        category = item.categoryName,
                        originalName = item.originalName,
                        mimeType = item.mimeType,
                        sizeBytes = item.sizeBytes,
                        encryptedFileName = "${item.id}.enc",
                        originalFolderHint = item.originalFolderHint,
                        aiCategoryTag = item.aiCategoryTag,
                        aiConfidence = item.aiConfidence,
                        isSoftDeleted = item.isSoftDeleted,
                        deletedAt = item.deletedAt,
                        importedAt = item.importedAt
                    )
                )
                restoredCount++
            }
        }
        for (app in parsedManifest.lockedApps) {
            dao.upsertLockedApp(
                LockedAppEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    isLocked = app.isLocked
                )
            )
        }
        prefs.applyBackupSettings(parsedManifest.settings)
        return restoredCount
    }

    private suspend fun importLegacyV1Payload(payload: SmartVaultBackupPackage): Int {
        var restoredCount = 0
        for (item in payload.items) {
            val rawPayload = Base64.decode(item.payloadBase64, Base64.NO_WRAP)
            val encFileName = "${item.id}.enc"
            VaultCryptoManager.encryptToFile(rawPayload, File(encryptedVaultDir, encFileName))
            dao.upsertItem(
                VaultItemEntity(
                    id = item.id,
                    category = item.categoryName,
                    originalName = item.originalName,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    encryptedFileName = encFileName,
                    originalFolderHint = item.originalFolderHint,
                    aiCategoryTag = item.aiCategoryTag,
                    aiConfidence = item.aiConfidence,
                    isSoftDeleted = item.isSoftDeleted,
                    deletedAt = item.deletedAt,
                    importedAt = item.importedAt
                )
            )
            restoredCount++
        }
        for (app in payload.lockedApps) {
            dao.upsertLockedApp(
                LockedAppEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    isLocked = app.isLocked
                )
            )
        }
        prefs.applyBackupSettings(payload.settings)
        return restoredCount
    }

    private class NonClosingInputStream(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() { /* Managed by outer ZipInputStream.closeEntry() */ }
    }
}
