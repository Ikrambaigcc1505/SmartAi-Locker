package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCryptoManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "SmartAIVaultMasterKey_AES256"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val PBKDF2_ITERATIONS = 100_000
    private const val STREAM_CHUNK_BYTES = 64 * 1024

    private val STREAM_FILE_MAGIC = "SVSTREAM2".toByteArray(Charsets.US_ASCII)
    const val BACKUP_MAGIC_V1 = "SMARTVAULT_AES256_V1"
    const val BACKUP_MAGIC_V2 = "SMARTVAULT_STREAM_V2"

    private val secureRandom = SecureRandom()

    fun generateSaltBase64(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashSecret(secret: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        return Base64.encodeToString(derived, Base64.NO_WRAP)
    }

    fun verifySecret(candidate: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (candidate.isEmpty()) return false
        val actual = Base64.decode(hashSecret(candidate, saltBase64), Base64.NO_WRAP)
        val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptBytesWithKeystore(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        return ByteBuffer.allocate(4 + iv.size + cipherText.size)
            .putInt(iv.size)
            .put(iv)
            .put(cipherText)
            .array()
    }

    fun decryptBytesWithKeystore(encryptedPayload: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encryptedPayload)
        val ivLen = buffer.int
        require(ivLen in 12..16) { "Invalid encrypted file header" }
        val iv = ByteArray(ivLen)
        buffer.get(iv)
        val cipherText = ByteArray(buffer.remaining())
        buffer.get(cipherText)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateMasterKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(cipherText)
    }

    fun encryptToFile(plainBytes: ByteArray, destFile: File) {
        destFile.parentFile?.mkdirs()
        if (plainBytes.size <= STREAM_CHUNK_BYTES) {
            destFile.writeBytes(encryptBytesWithKeystore(plainBytes))
        } else {
            ByteArrayInputStream(plainBytes).use { input ->
                encryptStreamToFile(input, destFile)
            }
        }
    }

    /**
     * Streams arbitrary-size input (e.g. 4K video) into an encrypted sandbox file using 64 KB
     * AES-256-GCM chunks via CipherOutputStream, keeping RAM usage flat.
     */
    fun encryptStreamToFile(inputStream: InputStream, destFile: File): Long {
        destFile.parentFile?.mkdirs()
        val masterKey = getOrCreateMasterKey()
        var totalPlainBytes = 0L
        FileOutputStream(destFile).use { fos ->
            val bos = BufferedOutputStream(fos, STREAM_CHUNK_BYTES)
            bos.write(STREAM_FILE_MAGIC)
            ChunkedGcmOutputStream(bos, masterKey, keyStoreRandomIv = true).use { gcmOut ->
                val buffer = ByteArray(STREAM_CHUNK_BYTES)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        gcmOut.write(buffer, 0, read)
                        totalPlainBytes += read
                    }
                }
            }
        }
        return totalPlainBytes
    }

    /**
     * Streams decrypted bytes from either SVSTREAM2 chunked files or legacy single-block .enc files
     * directly to [outputStream] via CipherInputStream.
     */
    fun decryptFileToStream(sourceFile: File, outputStream: OutputStream): Long {
        require(sourceFile.exists()) { "Encrypted vault payload is missing." }
        FileInputStream(sourceFile).use { fis ->
            val bis = BufferedInputStream(fis, STREAM_CHUNK_BYTES)
            bis.mark(STREAM_FILE_MAGIC.size + 4)
            val probe = ByteArray(STREAM_FILE_MAGIC.size)
            val probeRead = bis.read(probe)
            if (probeRead == STREAM_FILE_MAGIC.size && probe.contentEquals(STREAM_FILE_MAGIC)) {
                val masterKey = getOrCreateMasterKey()
                var total = 0L
                ChunkedGcmInputStream(bis, masterKey).use { gcmIn ->
                    val buffer = ByteArray(STREAM_CHUNK_BYTES)
                    while (true) {
                        val read = gcmIn.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            outputStream.write(buffer, 0, read)
                            total += read
                        }
                    }
                }
                outputStream.flush()
                return total
            } else {
                bis.reset()
                val legacyBytes = decryptBytesWithKeystore(bis.readBytes())
                outputStream.write(legacyBytes)
                outputStream.flush()
                return legacyBytes.size.toLong()
            }
        }
    }

    fun decryptFromFile(sourceFile: File): ByteArray {
        val out = ByteArrayOutputStream()
        decryptFileToStream(sourceFile, out)
        return out.toByteArray()
    }

    fun secureDeleteFile(file: File) {
        if (!file.exists()) return
        runCatching {
            val length = file.length().toInt().coerceAtMost(2 * 1024 * 1024)
            if (length > 0) {
                val zeros = ByteArray(length)
                file.writeBytes(zeros)
            }
        }
        file.delete()
    }

    /**
     * Opens a streaming AES-256-GCM .smartvault V2 encrypted output stream backed by PBKDF2 key derivation.
     */
    fun openStreamingBackupOutput(rawOut: OutputStream, passphrase: String): OutputStream {
        require(passphrase.length >= 4) { "Passphrase must be at least 4 characters." }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val key = deriveAes256Key(passphrase, salt)
        val dataOut = DataOutputStream(BufferedOutputStream(rawOut, STREAM_CHUNK_BYTES))
        val magic = BACKUP_MAGIC_V2.toByteArray(Charsets.UTF_8)
        dataOut.writeInt(magic.size)
        dataOut.write(magic)
        dataOut.writeInt(salt.size)
        dataOut.write(salt)
        dataOut.flush()
        return ChunkedGcmOutputStream(dataOut, key, keyStoreRandomIv = false)
    }

    /**
     * Inspects a .smartvault backup header and opens either a V2 streaming InputStream or reports V1.
     */
    sealed interface BackupStreamHandle {
        data class StreamingV2(val decryptedStream: InputStream) : BackupStreamHandle
        data class LegacyV1(val magicAlreadyConsumedInStream: InputStream) : BackupStreamHandle
    }

    fun openBackupInput(rawIn: InputStream, passphrase: String): BackupStreamHandle {
        val bis = BufferedInputStream(rawIn, STREAM_CHUNK_BYTES)
        val dataIn = DataInputStream(bis)
        val magicLen = dataIn.readInt()
        require(magicLen in 1..64) { "Corrupted backup file" }
        val magicBytes = ByteArray(magicLen)
        dataIn.readFully(magicBytes)
        val magic = String(magicBytes, Charsets.UTF_8)

        return when (magic) {
            BACKUP_MAGIC_V2 -> {
                val saltLen = dataIn.readInt()
                require(saltLen == 16) { "Invalid backup salt" }
                val salt = ByteArray(saltLen)
                dataIn.readFully(salt)
                val key = deriveAes256Key(passphrase, salt)
                BackupStreamHandle.StreamingV2(ChunkedGcmInputStream(dataIn, key))
            }
            BACKUP_MAGIC_V1 -> BackupStreamHandle.LegacyV1(dataIn)
            else -> throw IllegalArgumentException("Not a valid SmartAI .smartvault backup file")
        }
    }

    fun decryptLegacyV1PayloadAfterMagic(dataIn: InputStream, passphrase: String): ByteArray {
        val dis = DataInputStream(dataIn)
        val saltLen = dis.readInt()
        require(saltLen == 16) { "Invalid backup salt" }
        val salt = ByteArray(saltLen)
        dis.readFully(salt)

        val ivLen = dis.readInt()
        require(ivLen in 12..16) { "Invalid backup IV" }
        val iv = ByteArray(ivLen)
        dis.readFully(iv)

        val cipherText = dis.readBytes()
        val key = deriveAes256Key(passphrase, salt)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun encryptBackupStream(plainIn: InputStream, cipherOut: OutputStream, passphrase: String) {
        openStreamingBackupOutput(cipherOut, passphrase).use { encOut ->
            val buf = ByteArray(STREAM_CHUNK_BYTES)
            while (true) {
                val read = plainIn.read(buf)
                if (read < 0) break
                if (read > 0) encOut.write(buf, 0, read)
            }
        }
    }

    fun decryptBackupStream(cipherIn: InputStream, plainOut: OutputStream, passphrase: String) {
        when (val handle = openBackupInput(cipherIn, passphrase)) {
            is BackupStreamHandle.StreamingV2 -> {
                handle.decryptedStream.use { decIn ->
                    val buf = ByteArray(STREAM_CHUNK_BYTES)
                    while (true) {
                        val read = decIn.read(buf)
                        if (read < 0) break
                        if (read > 0) plainOut.write(buf, 0, read)
                    }
                    plainOut.flush()
                }
            }
            is BackupStreamHandle.LegacyV1 -> {
                val bytes = decryptLegacyV1PayloadAfterMagic(handle.magicAlreadyConsumedInStream, passphrase)
                plainOut.write(bytes)
                plainOut.flush()
            }
        }
    }

    /**
     * Encrypts portable .smartvault V1 bytes (kept for compatibility and unit-level checks).
     */
    fun encryptBackupWithPassphrase(plainBytes: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val key = deriveAes256Key(passphrase, salt)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plainBytes)

        val magicBytes = BACKUP_MAGIC_V1.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + magicBytes.size + 4 + salt.size + 4 + iv.size + cipherText.size)
            .putInt(magicBytes.size)
            .put(magicBytes)
            .putInt(salt.size)
            .put(salt)
            .putInt(iv.size)
            .put(iv)
            .put(cipherText)
            .array()
    }

    fun decryptBackupWithPassphrase(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        val out = ByteArrayOutputStream()
        decryptBackupStream(ByteArrayInputStream(encryptedBytes), out, passphrase)
        return out.toByteArray()
    }

    private fun deriveAes256Key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private class ChunkedGcmOutputStream(
        underlying: OutputStream,
        private val key: SecretKey,
        private val keyStoreRandomIv: Boolean
    ) : OutputStream() {
        private val dataOut = DataOutputStream(underlying)
        private val buffer = ByteArray(STREAM_CHUNK_BYTES)
        private var pos = 0
        private var chunkIndex = 0L
        private var closed = false

        override fun write(b: Int) {
            if (closed) throw IllegalStateException("Stream is closed")
            buffer[pos++] = b.toByte()
            if (pos == buffer.size) flushChunk()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IllegalStateException("Stream is closed")
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val space = buffer.size - pos
                val toCopy = minOf(space, remaining)
                System.arraycopy(b, offset, buffer, pos, toCopy)
                pos += toCopy
                offset += toCopy
                remaining -= toCopy
                if (pos == buffer.size) {
                    flushChunk()
                }
            }
        }

        private fun flushChunk() {
            if (pos == 0) return
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val iv: ByteArray
            if (keyStoreRandomIv) {
                cipher.init(Cipher.ENCRYPT_MODE, key)
                iv = cipher.iv
            } else {
                iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            cipher.updateAAD(longToBytes(chunkIndex++))

            val chunkBuffer = ByteArrayOutputStream(pos + 32)
            CipherOutputStream(chunkBuffer, cipher).use { cos ->
                cos.write(buffer, 0, pos)
            }
            val encryptedChunk = chunkBuffer.toByteArray()
            dataOut.writeInt(encryptedChunk.size)
            dataOut.writeInt(iv.size)
            dataOut.write(iv)
            dataOut.write(encryptedChunk)
            pos = 0
        }

        override fun flush() {
            dataOut.flush()
        }

        override fun close() {
            if (closed) return
            try {
                flushChunk()
                dataOut.writeInt(0) // explicit EOF marker to detect truncation
                dataOut.flush()
            } finally {
                closed = true
                dataOut.close()
            }
        }
    }

    private class ChunkedGcmInputStream(
        underlying: InputStream,
        private val key: SecretKey
    ) : InputStream() {
        private val dataIn = DataInputStream(underlying)
        private var currentPlain: ByteArray = ByteArray(0)
        private var currentPos = 0
        private var currentLen = 0
        private var chunkIndex = 0L
        private var eof = false

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!ensureChunk()) return -1
            val avail = currentLen - currentPos
            val toCopy = minOf(avail, len)
            System.arraycopy(currentPlain, currentPos, b, off, toCopy)
            currentPos += toCopy
            return toCopy
        }

        private fun ensureChunk(): Boolean {
            if (currentPos < currentLen) return true
            if (eof) return false

            val cipherLen = dataIn.readInt()
            if (cipherLen == 0) {
                eof = true
                return false
            }
            require(cipherLen in 16..(STREAM_CHUNK_BYTES * 2)) { "Corrupted encrypted chunk size: $cipherLen" }
            val ivLen = dataIn.readInt()
            require(ivLen in 12..16) { "Invalid chunk IV length" }
            val iv = ByteArray(ivLen)
            dataIn.readFully(iv)
            val cipherBytes = ByteArray(cipherLen)
            dataIn.readFully(cipherBytes)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(longToBytes(chunkIndex++))

            val plainOut = ByteArrayOutputStream(cipherLen)
            CipherInputStream(ByteArrayInputStream(cipherBytes), cipher).use { cis ->
                val buf = ByteArray(8192)
                while (true) {
                    val r = cis.read(buf)
                    if (r < 0) break
                    plainOut.write(buf, 0, r)
                }
            }
            // Note: CipherInputStream catches AEADBadTagException on some older Android JVMs and wraps in IOException or swallows if final block fails;
            // verify we produced expected plaintext length (cipherLen - 16 tag bytes) to guarantee GCM tag succeeded.
            val decrypted = plainOut.toByteArray()
            if (decrypted.size != cipherLen - 16) {
                throw javax.crypto.AEADBadTagException("GCM authentication tag verification failed")
            }

            currentPlain = decrypted
            currentPos = 0
            currentLen = decrypted.size
            if (currentLen == 0) {
                throw EOFException("Unexpected empty encrypted chunk")
            }
            return true
        }

        override fun close() {
            eof = true
            dataIn.close()
        }
    }

    private fun longToBytes(value: Long): ByteArray =
        ByteBuffer.allocate(8).putLong(value).array()
}
