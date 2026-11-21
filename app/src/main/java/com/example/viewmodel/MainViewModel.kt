package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.data.AccentTheme
import com.example.data.AiScanCandidate
import com.example.data.AuthMethod
import com.example.data.AutoLockPreset
import com.example.data.DisguiseMode
import com.example.data.InstalledAppInfo
import com.example.data.InstalledAppsRepository
import com.example.data.IntruderLogEntity
import com.example.data.LockedAppEntity
import com.example.data.SecurityConfigState
import com.example.data.SecurityPreferences
import com.example.data.SmartAiClassifier
import com.example.data.SystemPermissionStatus
import com.example.data.ThemeMode
import com.example.data.VaultCategory
import com.example.data.VaultDatabase
import com.example.data.VaultItemEntity
import com.example.data.VaultRepository
import com.example.security.AppDisguiseManager
import com.example.security.AppLockAccessibilityService
import com.example.security.AppLockMonitorService
import com.example.security.CalculatorEngine
import com.example.security.IntruderCameraHelper
import com.example.security.PanicLockEventBus
import com.example.security.PanicLockSensorService
import com.example.security.SystemPermissionsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppLockPhase {
    SPLASH,
    ONBOARDING,
    LOCKED,
    UNLOCKED
}

sealed interface PreviewPayload {
    data object None : PreviewPayload
    data class Loading(val item: VaultItemEntity) : PreviewPayload
    data class ImageBytes(val item: VaultItemEntity, val bytes: ByteArray) : PreviewPayload
    data class VideoFile(val item: VaultItemEntity, val tempFile: File) : PreviewPayload
    data class AudioFile(val item: VaultItemEntity, val tempFile: File) : PreviewPayload
    data class PdfFile(val item: VaultItemEntity, val tempFile: File) : PreviewPayload
    data class TextContent(val item: VaultItemEntity, val text: String) : PreviewPayload
    data class ArchiveOrBinary(
        val item: VaultItemEntity,
        val entries: List<String>,
        val infoSummary: String
    ) : PreviewPayload
    data class Error(val item: VaultItemEntity, val message: String) : PreviewPayload
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = SecurityPreferences(appContext)
    private val database = VaultDatabase.getInstance(appContext)
    private val repository = VaultRepository(appContext, database.vaultDao(), prefs)
    private val appsRepository = InstalledAppsRepository(appContext)

    val securityConfig: StateFlow<SecurityConfigState> = prefs.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SecurityConfigState()
    )

    val isPanicSensorMonitoring: StateFlow<Boolean> = PanicLockEventBus.isMonitoring

    private val _lockPhase = MutableStateFlow(AppLockPhase.SPLASH)
    val lockPhase: StateFlow<AppLockPhase> = _lockPhase.asStateFlow()

    private val _isDecoySession = MutableStateFlow(false)
    val isDecoySession: StateFlow<Boolean> = _isDecoySession.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _permissionStatus = MutableStateFlow(SystemPermissionsHelper.getStatus(appContext))
    val permissionStatus: StateFlow<SystemPermissionStatus> = _permissionStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _aiCandidates = MutableStateFlow<List<AiScanCandidate>>(emptyList())
    val aiCandidates: StateFlow<List<AiScanCandidate>> = _aiCandidates.asStateFlow()

    private val _previewPayload = MutableStateFlow<PreviewPayload>(PreviewPayload.None)
    val previewPayload: StateFlow<PreviewPayload> = _previewPayload.asStateFlow()

    private val _isMediaPlaying = MutableStateFlow(false)
    val isMediaPlaying: StateFlow<Boolean> = _isMediaPlaying.asStateFlow()

    private val _lockedTargetPackage = MutableStateFlow<String?>(null)
    val lockedTargetPackage: StateFlow<String?> = _lockedTargetPackage.asStateFlow()

    private val _lockedTargetAppLabel = MutableStateFlow<String?>(null)
    val lockedTargetAppLabel: StateFlow<String?> = _lockedTargetAppLabel.asStateFlow()

    private var lastBackgroundedAtMs: Long = 0L

    val activeVaultItems: StateFlow<List<VaultItemEntity>> = combine(
        repository.observeActiveItems(),
        _isDecoySession
    ) { items, isDecoy ->
        if (isDecoy) emptyList() else items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashItems: StateFlow<List<VaultItemEntity>> = combine(
        repository.observeTrashItems(),
        _isDecoySession
    ) { items, isDecoy ->
        if (isDecoy) emptyList() else items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lockedApps: StateFlow<List<LockedAppEntity>> = combine(
        repository.observeLockedApps(),
        _isDecoySession
    ) { apps, isDecoy ->
        if (isDecoy) emptyList() else apps
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val intruderLogs: StateFlow<List<IntruderLogEntity>> = combine(
        repository.observeIntruderLogs(),
        _isDecoySession
    ) { logs, isDecoy ->
        if (isDecoy) emptyList() else logs
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            delay(1_450L)
            val cfg = securityConfig.value
            _lockPhase.value = if (cfg.onboardingCompleted && cfg.hasMasterPin) {
                AppLockPhase.LOCKED
            } else {
                AppLockPhase.ONBOARDING
            }
        }
        refreshPermissionsAndApps()
    }

    fun clearStatusToast() {
        _statusToast.value = null
    }

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    fun setMediaPlaying(playing: Boolean) {
        _isMediaPlaying.value = playing
    }

    fun onAppBackgrounded() {
        val cfg = securityConfig.value
        if (!cfg.onboardingCompleted || _lockPhase.value != AppLockPhase.UNLOCKED) return
        lastBackgroundedAtMs = System.currentTimeMillis()
        val keepForPlayback = cfg.keepUnlockedDuringPlayback && _isMediaPlaying.value
        if (!keepForPlayback && cfg.autoLockSeconds == 0) {
            closePreview()
            AppLockAccessibilityService.clearUnlockedSessions()
            _lockPhase.value = AppLockPhase.LOCKED
            _isDecoySession.value = false
        }
    }

    fun onAppForegrounded() {
        refreshPermissionStatus()
        val cfg = securityConfig.value
        if (!cfg.onboardingCompleted || _lockPhase.value != AppLockPhase.UNLOCKED) return
        if (lastBackgroundedAtMs <= 0L) return
        val keepForPlayback = cfg.keepUnlockedDuringPlayback && _isMediaPlaying.value
        if (keepForPlayback) return

        val elapsedSeconds = (System.currentTimeMillis() - lastBackgroundedAtMs) / 1000L
        if (elapsedSeconds >= cfg.autoLockSeconds) {
            closePreview()
            AppLockAccessibilityService.clearUnlockedSessions()
            _lockPhase.value = AppLockPhase.LOCKED
            _isDecoySession.value = false
        }
        lastBackgroundedAtMs = 0L
    }

    fun lockVaultNow() {
        closePreview()
        AppLockAccessibilityService.clearUnlockedSessions()
        _isDecoySession.value = false
        _failedAttempts.value = 0
        _authErrorMessage.value = null
        _lockedTargetPackage.value = null
        _lockedTargetAppLabel.value = null
        if (securityConfig.value.onboardingCompleted) {
            _lockPhase.value = AppLockPhase.LOCKED
        }
    }

    fun triggerPanicLock(reason: com.example.security.PanicTriggerReason? = null) {
        closePreview()
        AppLockAccessibilityService.clearUnlockedSessions()
        _isDecoySession.value = false
        _failedAttempts.value = 0
        _authErrorMessage.value = null
        _lockedTargetPackage.value = null
        _lockedTargetAppLabel.value = null
        // Terminate session silently with zero visible toast/banner for stealth security
        _statusToast.value = null
        if (securityConfig.value.onboardingCompleted) {
            _lockPhase.value = AppLockPhase.LOCKED
        }
    }

    fun onExternalLockedAppIntercepted(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val label = runCatching {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrElse { packageName.substringAfterLast('.') }

        _lockedTargetPackage.value = packageName
        _lockedTargetAppLabel.value = label
        if (securityConfig.value.onboardingCompleted) {
            _lockPhase.value = AppLockPhase.LOCKED
        }
    }

    fun dismissLockedTargetAfterCancel() {
        _lockedTargetPackage.value = null
        _lockedTargetAppLabel.value = null
    }

    fun completeOnboarding(
        pin: String,
        pinLength: Int,
        pattern: String,
        question1: String,
        answer1: String,
        question2: String,
        answer2: String,
        enableBiometric: Boolean,
        decoyPin: String?
    ) {
        viewModelScope.launch {
            prefs.saveMasterPin(pin, pinLength)
            prefs.savePattern(pattern)
            prefs.saveSecurityQuestions(question1, answer1, question2, answer2)
            if (!decoyPin.isNullOrBlank() && decoyPin != pin) {
                prefs.saveDecoyPin(decoyPin, enabled = true)
            }
            prefs.completeOnboarding(enableBiometric)
            _isDecoySession.value = false
            _lockPhase.value = AppLockPhase.UNLOCKED
            _statusToast.value = "SmartAI Vault initialized with AES-256 local encryption"
            refreshPermissionsAndApps()
        }
    }

    suspend fun handleCalculatorEquals(rawExpression: String): Boolean {
        val candidate = CalculatorEngine.extractPinCandidate(rawExpression) ?: return false
        if (prefs.verifyMasterPin(candidate)) {
            completeUnlock(decoy = false)
            return true
        }
        if (prefs.verifyDecoyPin(candidate)) {
            completeUnlock(decoy = true)
            return true
        }
        return false
    }

    fun submitPinAttempt(pin: String, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            if (prefs.verifyMasterPin(pin)) {
                completeUnlock(decoy = false)
                return@launch
            }
            if (prefs.verifyDecoyPin(pin)) {
                completeUnlock(decoy = true)
                return@launch
            }
            handleFailedUnlockAttempt(AuthMethod.PIN, lifecycleOwner)
        }
    }

    fun submitPatternAttempt(patternDigits: String, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            if (prefs.verifyPattern(patternDigits)) {
                completeUnlock(decoy = false)
                return@launch
            }
            handleFailedUnlockAttempt(AuthMethod.PATTERN, lifecycleOwner)
        }
    }

    fun unlockViaBiometric() {
        completeUnlock(decoy = false)
    }

    private fun completeUnlock(decoy: Boolean) {
        _isDecoySession.value = decoy
        _failedAttempts.value = 0
        _authErrorMessage.value = null
        _lockedTargetPackage.value?.let { pkg ->
            AppLockAccessibilityService.markPackageTemporarilyUnlocked(pkg)
        }
        _lockedTargetPackage.value = null
        _lockedTargetAppLabel.value = null
        _lockPhase.value = AppLockPhase.UNLOCKED
        if (!decoy) {
            refreshPermissionsAndApps()
        }
    }

    private fun handleFailedUnlockAttempt(method: AuthMethod, lifecycleOwner: LifecycleOwner) {
        val nextCount = _failedAttempts.value + 1
        _failedAttempts.value = nextCount
        // Never reveal attempt threshold or intruder capture to the person trying to unlock
        _authErrorMessage.value = "Incorrect ${method.label}. Please try again."

        if (nextCount >= 3) {
            _failedAttempts.value = 0
            val selfieEnabled = securityConfig.value.intruderSelfieEnabled
            if (selfieEnabled) {
                IntruderCameraHelper.captureIntruderSelfie(
                    context = appContext,
                    lifecycleOwner = lifecycleOwner
                ) { result ->
                    viewModelScope.launch {
                        repository.logIntruderAttempt(
                            method = method,
                            failedCount = 3,
                            encryptedPhotoFileName = result.encryptedFileName,
                            note = result.statusNote
                        )
                    }
                }
            } else {
                viewModelScope.launch {
                    repository.logIntruderAttempt(
                        method = method,
                        failedCount = 3,
                        encryptedPhotoFileName = null,
                        note = "Selfie capture disabled — timestamp logged"
                    )
                }
            }
        }
    }

    fun triggerManualIntruderSelfieTest(lifecycleOwner: LifecycleOwner) {
        if (!IntruderCameraHelper.hasCameraPermission(appContext)) {
            _statusToast.value = "Grant Camera permission to capture intruder selfies."
            return
        }
        IntruderCameraHelper.captureIntruderSelfie(appContext, lifecycleOwner) { result ->
            viewModelScope.launch {
                repository.logIntruderAttempt(
                    method = AuthMethod.PIN,
                    failedCount = 3,
                    encryptedPhotoFileName = result.encryptedFileName,
                    note = result.statusNote
                )
                _statusToast.value = if (result.encryptedFileName != null) {
                    "Encrypted intruder selfie captured locally."
                } else {
                    result.statusNote
                }
            }
        }
    }

    suspend fun verifySecurityAnswers(answer1: String, answer2: String): Boolean {
        return prefs.verifySecurityQuestions(answer1, answer2)
    }

    suspend fun verifyMasterPinDirect(pin: String): Boolean {
        return prefs.verifyMasterPin(pin)
    }

    fun resetMasterPinAndPattern(newPin: String, pinLength: Int, newPattern: String?) {
        viewModelScope.launch {
            if (newPin.isNotBlank()) {
                prefs.saveMasterPin(newPin, pinLength)
            }
            if (!newPattern.isNullOrBlank() && newPattern.length >= 4) {
                prefs.savePattern(newPattern)
            }
            _failedAttempts.value = 0
            _authErrorMessage.value = null
            _statusToast.value = if (newPin.isNotBlank()) "Master PIN updated securely" else "Pattern Lock updated securely"
        }
    }

    fun updateSecurityQuestions(q1: String, a1: String, q2: String, a2: String) {
        viewModelScope.launch {
            prefs.saveSecurityQuestions(q1, a1, q2, a2)
            _statusToast.value = "Local security questions updated"
        }
    }

    fun updateDecoyPin(decoyPin: String, enabled: Boolean) {
        viewModelScope.launch {
            if (decoyPin.isNotBlank()) {
                if (prefs.verifyMasterPin(decoyPin)) {
                    _statusToast.value = "Decoy PIN must differ from Master PIN"
                    return@launch
                }
                prefs.saveDecoyPin(decoyPin, enabled)
            } else {
                prefs.setDecoyEnabled(enabled)
            }
            _statusToast.value = if (enabled) "Decoy Vault PIN active" else "Decoy Vault disabled"
        }
    }

    fun toggleDecoyVault(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDecoyEnabled(enabled)
        }
    }

    fun importFilesToVault(uris: List<Uri>, targetCategory: VaultCategory) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            var successCount = 0
            for (uri in uris) {
                val res = repository.importFromUri(uri, targetCategory)
                if (res.isSuccess) successCount++
            }
            _isBusy.value = false
            _statusToast.value = if (successCount > 0) {
                "Encrypted & hidden $successCount file(s) in ${targetCategory.title}"
            } else {
                "Could not import selected file(s)."
            }
        }
    }

    fun openVaultItem(item: VaultItemEntity) {
        viewModelScope.launch {
            _previewPayload.value = PreviewPayload.Loading(item)
            try {
                val category = VaultCategory.entries.firstOrNull { it.name == item.category }
                    ?: VaultCategory.DOCUMENTS
                when (category) {
                    VaultCategory.PHOTOS -> {
                        val bytes = repository.readDecryptedBytes(item)
                        _previewPayload.value = PreviewPayload.ImageBytes(item, bytes)
                    }
                    VaultCategory.VIDEOS -> {
                        val file = repository.createTempStreamFile(item)
                        _isMediaPlaying.value = true
                        _previewPayload.value = PreviewPayload.VideoFile(item, file)
                    }
                    VaultCategory.AUDIO -> {
                        val file = repository.createTempStreamFile(item)
                        _isMediaPlaying.value = true
                        _previewPayload.value = PreviewPayload.AudioFile(item, file)
                    }
                    VaultCategory.DOCUMENTS -> {
                        val lower = item.originalName.lowercase()
                        when {
                            item.mimeType == "application/pdf" || lower.endsWith(".pdf") -> {
                                val file = repository.createTempStreamFile(item)
                                _previewPayload.value = PreviewPayload.PdfFile(item, file)
                            }
                            item.mimeType.startsWith("text/") ||
                                lower.endsWith(".txt") || lower.endsWith(".json") ||
                                lower.endsWith(".csv") || lower.endsWith(".xml") ||
                                lower.endsWith(".log") || lower.endsWith(".md") -> {
                                val bytes = repository.readDecryptedBytes(item)
                                _previewPayload.value = PreviewPayload.TextContent(
                                    item,
                                    String(bytes, Charsets.UTF_8)
                                )
                            }
                            lower.endsWith(".zip") -> {
                                val bytes = repository.readDecryptedBytes(item)
                                val entries = mutableListOf<String>()
                                runCatching {
                                    java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                                        var entry = zis.nextEntry
                                        while (entry != null && entries.size < 40) {
                                            entries += entry.name
                                            entry = zis.nextEntry
                                        }
                                    }
                                }
                                _previewPayload.value = PreviewPayload.ArchiveOrBinary(
                                    item = item,
                                    entries = entries,
                                    infoSummary = "ZIP Archive • ${entries.size} entries inspected inside encrypted sandbox"
                                )
                            }
                            else -> {
                                _previewPayload.value = PreviewPayload.ArchiveOrBinary(
                                    item = item,
                                    entries = emptyList(),
                                    infoSummary = "Encrypted ${item.mimeType} document. Restore to device storage to open with external office apps."
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _previewPayload.value = PreviewPayload.Error(
                    item,
                    e.localizedMessage ?: "Unable to decrypt file."
                )
            }
        }
    }

    fun closePreview() {
        _isMediaPlaying.value = false
        _previewPayload.value = PreviewPayload.None
        repository.clearTempStreamCache()
    }

    fun unhideItem(item: VaultItemEntity) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.unhideAndRestoreToDevice(item)
            _isBusy.value = false
            res.onSuccess { folder ->
                _statusToast.value = "Restored ${item.originalName} to $folder"
            }.onFailure { err ->
                _statusToast.value = err.localizedMessage ?: "Restore failed"
            }
        }
    }

    fun softDeleteItem(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.softDeleteItem(item.id)
            _statusToast.value = "Moved ${item.originalName} to Trash Bin"
        }
    }

    fun restoreItemFromTrash(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.restoreFromTrash(item.id)
            _statusToast.value = "Restored ${item.originalName} to Vault"
        }
    }

    fun permanentDeleteItem(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.permanentDeleteItem(item)
            _statusToast.value = "Permanently erased ${item.originalName}"
        }
    }

    fun refreshPermissionStatus() {
        _permissionStatus.value = SystemPermissionsHelper.getStatus(appContext)
    }

    fun refreshPermissionsAndApps() {
        refreshPermissionStatus()
        viewModelScope.launch {
            val lockedSet = lockedApps.value.filter { it.isLocked }.map { it.packageName }.toSet()
            _installedApps.value = appsRepository.loadInstalledApps(lockedSet)
        }
    }

    fun toggleAppLock(app: InstalledAppInfo, locked: Boolean) {
        viewModelScope.launch {
            repository.toggleLockedApp(app.packageName, app.appName, locked)
            val updated = _installedApps.value.map {
                if (it.packageName == app.packageName) it.copy(isLocked = locked) else it
            }
            _installedApps.value = updated
            if (updated.any { it.isLocked } && SystemPermissionsHelper.hasUsageStatsPermission(appContext)) {
                AppLockMonitorService.startIfPermitted(appContext)
            }
        }
    }

    fun toggleConnectivityLock(
        wifi: Boolean? = null,
        mobileData: Boolean? = null,
        bluetooth: Boolean? = null,
        usb: Boolean? = null,
        settingsApp: Boolean? = null,
        notificationPanel: Boolean? = null
    ) {
        viewModelScope.launch {
            prefs.setConnectivityLock(
                wifi = wifi,
                mobileData = mobileData,
                bluetooth = bluetooth,
                usb = usb,
                settingsApp = settingsApp,
                notificationPanel = notificationPanel
            )
        }
    }

    fun scanCandidateUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val scanned = uris.map { uri -> SmartAiClassifier.inspectUriForSuggestion(appContext, uri) }
                .sortedByDescending { it.confidence }
            _aiCandidates.value = scanned
            _isBusy.value = false
            _statusToast.value = "On-device SmartAI scanned ${scanned.size} file(s)"
        }
    }

    fun autoHideCandidate(candidate: AiScanCandidate) {
        val uriString = candidate.sourceUriString ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.importFromUri(Uri.parse(uriString), candidate.recommendedCategory)
            _isBusy.value = false
            res.onSuccess { entity ->
                _aiCandidates.value = _aiCandidates.value.map {
                    if (it.id == candidate.id) it.copy(isAlreadyVaulted = true, existingVaultItemId = entity.id)
                    else it
                }
                _statusToast.value = "Auto-hidden ${candidate.displayName} (${candidate.aiTag})"
            }.onFailure {
                _statusToast.value = "Failed to auto-hide ${candidate.displayName}"
            }
        }
    }

    fun autoHideAllSuggested() {
        val pending = _aiCandidates.value.filter { !it.isAlreadyVaulted && it.sourceUriString != null }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            var count = 0
            val updated = _aiCandidates.value.toMutableList()
            for (index in updated.indices) {
                val cand = updated[index]
                if (!cand.isAlreadyVaulted && cand.sourceUriString != null) {
                    val res = repository.importFromUri(
                        Uri.parse(cand.sourceUriString),
                        cand.recommendedCategory
                    )
                    if (res.isSuccess) {
                        count++
                        updated[index] = cand.copy(
                            isAlreadyVaulted = true,
                            existingVaultItemId = res.getOrNull()?.id
                        )
                    }
                }
            }
            _aiCandidates.value = updated
            _isBusy.value = false
            _statusToast.value = "SmartAI encrypted & hid $count sensitive file(s)"
        }
    }

    fun reclassifyVaultItems() {
        viewModelScope.launch {
            _isBusy.value = true
            val count = repository.runOnDeviceVaultCategorization()
            _isBusy.value = false
            _statusToast.value = "On-device SmartAI analyzed vault ($count tags refined)"
        }
    }

    suspend fun loadDecryptedIntruderPhoto(fileName: String?): ByteArray? {
        if (fileName.isNullOrBlank()) return null
        return repository.readIntruderPhotoBytes(fileName)
    }

    fun deleteIntruderLog(log: IntruderLogEntity) {
        viewModelScope.launch { repository.deleteIntruderLog(log) }
    }

    fun clearAllIntruderLogs() {
        viewModelScope.launch {
            repository.clearAllIntruderLogs()
            _statusToast.value = "All intruder logs erased"
        }
    }

    fun exportEncryptedBackup(passphrase: String, customUri: Uri? = null) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.exportEncryptedBackupToDownloads(passphrase, customUri)
            _isBusy.value = false
            res.onSuccess { path ->
                _statusToast.value = "AES-256 backup saved to $path"
            }.onFailure { err ->
                _statusToast.value = err.localizedMessage ?: "Backup export failed"
            }
        }
    }

    fun importEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.importEncryptedBackup(uri, passphrase)
            _isBusy.value = false
            res.onSuccess { count ->
                _statusToast.value = "Restored $count encrypted item(s) from .smartvault backup"
            }.onFailure { err ->
                _statusToast.value = "Invalid passphrase or backup file: ${err.localizedMessage}"
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricEnabled(enabled) }
    }

    fun setPreferredAuthMethod(method: AuthMethod) {
        viewModelScope.launch { prefs.setPreferredAuthMethod(method) }
    }

    fun setAutoLockTimer(preset: AutoLockPreset, customSeconds: Int? = null) {
        viewModelScope.launch {
            prefs.setAutoLockTimer(preset, customSeconds)
            _statusToast.value = "Auto-lock timer updated"
        }
    }

    fun setKeepUnlockedDuringPlayback(enabled: Boolean) {
        viewModelScope.launch { prefs.setKeepUnlockedDuringPlayback(enabled) }
    }

    fun setBackgroundAudioPlay(enabled: Boolean) {
        viewModelScope.launch { prefs.setBackgroundAudioPlay(enabled) }
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setFlagSecureEnabled(enabled) }
    }

    fun setIntruderSelfieEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setIntruderSelfieEnabled(enabled) }
    }

    fun setRandomizeKeypad(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setRandomizeKeypad(enabled)
            _statusToast.value = if (enabled) "Randomized PIN keypad enabled" else "Standard 0-9 keypad layout restored"
        }
    }

    fun setFakeCrashEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setFakeCrashEnabled(enabled)
            _statusToast.value = if (enabled) "Fake Crash cover enabled (Long-press OK to unlock)" else "Fake Crash cover disabled"
        }
    }

    fun setPanicLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPanicLockEnabled(enabled)
            if (enabled && _lockPhase.value == AppLockPhase.UNLOCKED) {
                PanicLockSensorService.start(appContext)
            } else if (!enabled) {
                PanicLockSensorService.stop(appContext)
            }
            _statusToast.value = if (enabled) {
                "Panic Gesture armed — monitoring face-down & shake sensors"
            } else {
                "Panic Gesture disabled — sensor monitoring stopped"
            }
        }
    }

    fun setDisguiseMode(mode: DisguiseMode) {
        viewModelScope.launch {
            prefs.setDisguiseMode(mode)
            val applied = AppDisguiseManager.applyLauncherDisguise(appContext, mode)
            _statusToast.value = if (applied) {
                "Launcher disguised as ${mode.launcherLabel}. Home icon may take a few seconds to refresh."
            } else {
                "Saved ${mode.title} disguise preference"
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setAccentTheme(accent: AccentTheme) {
        viewModelScope.launch { prefs.setAccentTheme(accent) }
    }

    fun unlockProWithReward() {
        viewModelScope.launch {
            prefs.unlockProFeatures()
            _statusToast.value = "Pro Vault Themes & Deep AI Scan unlocked!"
        }
    }
}
