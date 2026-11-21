package com.example.data

import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable

enum class VaultCategory(
    val title: String,
    val subtitle: String,
    val supportedFormats: String
) {
    PHOTOS(
        title = "Photos & Images",
        subtitle = "Encrypted private gallery",
        supportedFormats = "JPG, PNG, WEBP, GIF, RAW"
    ),
    VIDEOS(
        title = "Videos",
        subtitle = "Sandboxed media vault",
        supportedFormats = "MP4, MKV, AVI, MOV"
    ),
    AUDIO(
        title = "Audio & Recordings",
        subtitle = "Protected voice & audio",
        supportedFormats = "MP3, WAV, AAC, M4A"
    ),
    DOCUMENTS(
        title = "Documents & PDFs",
        subtitle = "Encrypted files & archives",
        supportedFormats = "PDF, DOCX, XLSX, PPTX, TXT, ZIP"
    )
}

enum class AuthMethod(val label: String) {
    PIN("Numeric PIN"),
    PATTERN("Pattern Lock")
}

enum class AutoLockPreset(val label: String, val seconds: Int) {
    INSTANT("Instant Lock", 0),
    ONE_MIN("1 Minute", 60),
    THREE_MIN("3 Minutes", 180),
    FIVE_MIN("5 Minutes", 300),
    CUSTOM("Custom Time", -1)
}

enum class ThemeMode(val label: String) {
    DARK("Stealth Dark"),
    LIGHT("Frost Light"),
    SYSTEM("System Default")
}

enum class AccentTheme(val label: String, val isPro: Boolean) {
    CYBER_CYAN("Cyber Cyan", false),
    EMERALD_SHIELD("Emerald Shield", true),
    ROYAL_AMETHYST("Royal Amethyst", true)
}

enum class DisguiseMode(
    val launcherLabel: String,
    val title: String,
    val subtitle: String,
    val aliasClassName: String
) {
    DEFAULT(
        launcherLabel = "SmartAi Locker",
        title = "SmartAi Locker",
        subtitle = "Default Vault Shield icon & standard unlock screen",
        aliasClassName = "com.example.DefaultLauncherAlias"
    ),
    CALCULATOR(
        launcherLabel = "Calculator",
        title = "Secret Calculator",
        subtitle = "Opens working calculator. Type your PIN + '=' to unlock",
        aliasClassName = "com.example.CalculatorLauncherAlias"
    ),
    NOTES(
        launcherLabel = "Field Notes",
        title = "Compass / Notes",
        subtitle = "Disguises launcher icon and title as Field Notes utility",
        aliasClassName = "com.example.NotesLauncherAlias"
    )
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val isLocked: Boolean
)

data class SystemPermissionStatus(
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val deviceAdminEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val cameraGranted: Boolean = false,
    val biometricHardwareReady: Boolean = false
)

data class AiScanCandidate(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceUriString: String?,
    val recommendedCategory: VaultCategory,
    val aiTag: String,
    val confidence: Float,
    val reason: String,
    val isAlreadyVaulted: Boolean = false,
    val existingVaultItemId: String? = null
)

@Serializable
data class SmartVaultBackupPackage(
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String = "1.0",
    val items: List<BackupItemRecord>,
    val lockedApps: List<BackupAppLockRecord>,
    val settings: BackupSettingsRecord
)

@Serializable
data class SmartVaultBackupManifest(
    val version: Int = 2,
    val exportedAt: Long,
    val appVersion: String = "1.0",
    val items: List<BackupItemMetadataRecord>,
    val lockedApps: List<BackupAppLockRecord>,
    val settings: BackupSettingsRecord
)

@Serializable
data class BackupItemRecord(
    val id: String,
    val categoryName: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val originalFolderHint: String,
    val aiCategoryTag: String,
    val aiConfidence: Float,
    val isSoftDeleted: Boolean,
    val deletedAt: Long?,
    val importedAt: Long,
    val payloadBase64: String
)

@Serializable
data class BackupItemMetadataRecord(
    val id: String,
    val categoryName: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val originalFolderHint: String,
    val aiCategoryTag: String,
    val aiConfidence: Float,
    val isSoftDeleted: Boolean,
    val deletedAt: Long?,
    val importedAt: Long,
    val archiveEntryName: String
)

@Serializable
data class BackupAppLockRecord(
    val packageName: String,
    val appName: String,
    val isLocked: Boolean
)

@Serializable
data class BackupSettingsRecord(
    val pinLength: Int,
    val autoLockSeconds: Int,
    val wifiLocked: Boolean,
    val mobileDataLocked: Boolean,
    val bluetoothLocked: Boolean,
    val usbLocked: Boolean,
    val settingsAppLocked: Boolean,
    val notificationPanelLocked: Boolean,
    val keepUnlockedDuringPlayback: Boolean,
    val backgroundAudioPlay: Boolean,
    val randomizeKeypad: Boolean = false,
    val fakeCrashEnabled: Boolean = false,
    val panicLockEnabled: Boolean = false,
    val disguiseModeName: String = DisguiseMode.DEFAULT.name
)
