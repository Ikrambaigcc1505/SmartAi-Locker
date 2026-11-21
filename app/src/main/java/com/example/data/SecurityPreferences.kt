package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore by preferencesDataStore(name = "smartai_security_prefs")

data class SecurityConfigState(
    val onboardingCompleted: Boolean = false,
    val pinLength: Int = 4,
    val hasMasterPin: Boolean = false,
    val hasPattern: Boolean = false,
    val hasDecoyPin: Boolean = false,
    val decoyEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val preferredAuthMethod: AuthMethod = AuthMethod.PIN,
    val autoLockPreset: AutoLockPreset = AutoLockPreset.INSTANT,
    val autoLockSeconds: Int = 0,
    val securityQuestion1: String = "What was your childhood nickname?",
    val securityQuestion2: String = "What city were you born in?",
    val hasSecurityQuestions: Boolean = false,
    val wifiLocked: Boolean = false,
    val mobileDataLocked: Boolean = false,
    val bluetoothLocked: Boolean = false,
    val usbLocked: Boolean = false,
    val settingsAppLocked: Boolean = false,
    val notificationPanelLocked: Boolean = false,
    val keepUnlockedDuringPlayback: Boolean = true,
    val backgroundAudioPlay: Boolean = false,
    val flagSecureEnabled: Boolean = true,
    val intruderSelfieEnabled: Boolean = true,
    val randomizeKeypad: Boolean = false,
    val fakeCrashEnabled: Boolean = false,
    val panicLockEnabled: Boolean = true,
    val disguiseMode: DisguiseMode = DisguiseMode.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentTheme: AccentTheme = AccentTheme.CYBER_CYAN,
    val proFeaturesUnlocked: Boolean = false
)

class SecurityPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val MASTER_PIN_HASH = stringPreferencesKey("master_pin_hash")
        val MASTER_PIN_SALT = stringPreferencesKey("master_pin_salt")
        val PATTERN_HASH = stringPreferencesKey("pattern_hash")
        val PATTERN_SALT = stringPreferencesKey("pattern_salt")
        val DECOY_PIN_HASH = stringPreferencesKey("decoy_pin_hash")
        val DECOY_PIN_SALT = stringPreferencesKey("decoy_pin_salt")
        val DECOY_ENABLED = booleanPreferencesKey("decoy_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PREFERRED_AUTH = stringPreferencesKey("preferred_auth")
        val AUTO_LOCK_PRESET = stringPreferencesKey("auto_lock_preset")
        val AUTO_LOCK_SECONDS = intPreferencesKey("auto_lock_seconds")
        val SEC_Q1 = stringPreferencesKey("sec_q1")
        val SEC_Q1_HASH = stringPreferencesKey("sec_q1_hash")
        val SEC_Q1_SALT = stringPreferencesKey("sec_q1_salt")
        val SEC_Q2 = stringPreferencesKey("sec_q2")
        val SEC_Q2_HASH = stringPreferencesKey("sec_q2_hash")
        val SEC_Q2_SALT = stringPreferencesKey("sec_q2_salt")
        val WIFI_LOCKED = booleanPreferencesKey("wifi_locked")
        val MOBILE_DATA_LOCKED = booleanPreferencesKey("mobile_data_locked")
        val BLUETOOTH_LOCKED = booleanPreferencesKey("bluetooth_locked")
        val USB_LOCKED = booleanPreferencesKey("usb_locked")
        val SETTINGS_LOCKED = booleanPreferencesKey("settings_locked")
        val NOTIFICATION_LOCKED = booleanPreferencesKey("notification_locked")
        val KEEP_UNLOCKED_PLAYBACK = booleanPreferencesKey("keep_unlocked_playback")
        val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
        val FLAG_SECURE = booleanPreferencesKey("flag_secure")
        val INTRUDER_SELFIE = booleanPreferencesKey("intruder_selfie")
        val RANDOMIZE_KEYPAD = booleanPreferencesKey("randomize_keypad")
        val FAKE_CRASH_ENABLED = booleanPreferencesKey("fake_crash_enabled")
        val PANIC_LOCK_ENABLED = booleanPreferencesKey("panic_lock_enabled")
        val DISGUISE_MODE = stringPreferencesKey("disguise_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val PRO_UNLOCKED = booleanPreferencesKey("pro_unlocked")
    }

    val configFlow: Flow<SecurityConfigState> = context.securityDataStore.data.map { prefs ->
        val preset = prefs[Keys.AUTO_LOCK_PRESET]?.let { name ->
            AutoLockPreset.entries.firstOrNull { it.name == name }
        } ?: AutoLockPreset.INSTANT

        val preferredAuth = prefs[Keys.PREFERRED_AUTH]?.let { name ->
            AuthMethod.entries.firstOrNull { it.name == name }
        } ?: AuthMethod.PIN

        val disguiseMode = prefs[Keys.DISGUISE_MODE]?.let { name ->
            DisguiseMode.entries.firstOrNull { it.name == name }
        } ?: DisguiseMode.DEFAULT

        val themeMode = prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.DARK

        val accentTheme = prefs[Keys.ACCENT_THEME]?.let { name ->
            AccentTheme.entries.firstOrNull { it.name == name }
        } ?: AccentTheme.CYBER_CYAN

        SecurityConfigState(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            pinLength = prefs[Keys.PIN_LENGTH] ?: 4,
            hasMasterPin = !prefs[Keys.MASTER_PIN_HASH].isNullOrEmpty(),
            hasPattern = !prefs[Keys.PATTERN_HASH].isNullOrEmpty(),
            hasDecoyPin = !prefs[Keys.DECOY_PIN_HASH].isNullOrEmpty(),
            decoyEnabled = prefs[Keys.DECOY_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            preferredAuthMethod = preferredAuth,
            autoLockPreset = preset,
            autoLockSeconds = prefs[Keys.AUTO_LOCK_SECONDS] ?: 0,
            securityQuestion1 = prefs[Keys.SEC_Q1] ?: "What was your childhood nickname?",
            securityQuestion2 = prefs[Keys.SEC_Q2] ?: "What city were you born in?",
            hasSecurityQuestions = !prefs[Keys.SEC_Q1_HASH].isNullOrEmpty() &&
                !prefs[Keys.SEC_Q2_HASH].isNullOrEmpty(),
            wifiLocked = prefs[Keys.WIFI_LOCKED] ?: false,
            mobileDataLocked = prefs[Keys.MOBILE_DATA_LOCKED] ?: false,
            bluetoothLocked = prefs[Keys.BLUETOOTH_LOCKED] ?: false,
            usbLocked = prefs[Keys.USB_LOCKED] ?: false,
            settingsAppLocked = prefs[Keys.SETTINGS_LOCKED] ?: false,
            notificationPanelLocked = prefs[Keys.NOTIFICATION_LOCKED] ?: false,
            keepUnlockedDuringPlayback = prefs[Keys.KEEP_UNLOCKED_PLAYBACK] ?: true,
            backgroundAudioPlay = prefs[Keys.BACKGROUND_AUDIO] ?: false,
            flagSecureEnabled = prefs[Keys.FLAG_SECURE] ?: true,
            intruderSelfieEnabled = prefs[Keys.INTRUDER_SELFIE] ?: true,
            randomizeKeypad = prefs[Keys.RANDOMIZE_KEYPAD] ?: false,
            fakeCrashEnabled = prefs[Keys.FAKE_CRASH_ENABLED] ?: false,
            panicLockEnabled = prefs[Keys.PANIC_LOCK_ENABLED] ?: true,
            disguiseMode = disguiseMode,
            themeMode = themeMode,
            accentTheme = accentTheme,
            proFeaturesUnlocked = prefs[Keys.PRO_UNLOCKED] ?: false
        )
    }

    suspend fun saveMasterPin(pin: String, pinLength: Int) {
        val salt = VaultCryptoManager.generateSaltBase64()
        val hash = VaultCryptoManager.hashSecret(pin, salt)
        context.securityDataStore.edit { prefs ->
            prefs[Keys.PIN_LENGTH] = pinLength
            prefs[Keys.MASTER_PIN_SALT] = salt
            prefs[Keys.MASTER_PIN_HASH] = hash
        }
    }

    suspend fun verifyMasterPin(pin: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val salt = prefs[Keys.MASTER_PIN_SALT] ?: return false
        val expected = prefs[Keys.MASTER_PIN_HASH] ?: return false
        return VaultCryptoManager.verifySecret(pin, salt, expected)
    }

    suspend fun savePattern(patternDigits: String) {
        val salt = VaultCryptoManager.generateSaltBase64()
        val hash = VaultCryptoManager.hashSecret(patternDigits, salt)
        context.securityDataStore.edit { prefs ->
            prefs[Keys.PATTERN_SALT] = salt
            prefs[Keys.PATTERN_HASH] = hash
        }
    }

    suspend fun verifyPattern(patternDigits: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val salt = prefs[Keys.PATTERN_SALT] ?: return false
        val expected = prefs[Keys.PATTERN_HASH] ?: return false
        return VaultCryptoManager.verifySecret(patternDigits, salt, expected)
    }

    suspend fun saveDecoyPin(decoyPin: String, enabled: Boolean) {
        val salt = VaultCryptoManager.generateSaltBase64()
        val hash = VaultCryptoManager.hashSecret(decoyPin, salt)
        context.securityDataStore.edit { prefs ->
            prefs[Keys.DECOY_PIN_SALT] = salt
            prefs[Keys.DECOY_PIN_HASH] = hash
            prefs[Keys.DECOY_ENABLED] = enabled
        }
    }

    suspend fun setDecoyEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.DECOY_ENABLED] = enabled }
    }

    suspend fun verifyDecoyPin(pin: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        if (prefs[Keys.DECOY_ENABLED] != true) return false
        val salt = prefs[Keys.DECOY_PIN_SALT] ?: return false
        val expected = prefs[Keys.DECOY_PIN_HASH] ?: return false
        return VaultCryptoManager.verifySecret(pin, salt, expected)
    }

    suspend fun saveSecurityQuestions(
        question1: String,
        answer1: String,
        question2: String,
        answer2: String
    ) {
        val norm1 = answer1.trim().lowercase()
        val norm2 = answer2.trim().lowercase()
        val salt1 = VaultCryptoManager.generateSaltBase64()
        val hash1 = VaultCryptoManager.hashSecret(norm1, salt1)
        val salt2 = VaultCryptoManager.generateSaltBase64()
        val hash2 = VaultCryptoManager.hashSecret(norm2, salt2)
        context.securityDataStore.edit { prefs ->
            prefs[Keys.SEC_Q1] = question1.trim()
            prefs[Keys.SEC_Q1_SALT] = salt1
            prefs[Keys.SEC_Q1_HASH] = hash1
            prefs[Keys.SEC_Q2] = question2.trim()
            prefs[Keys.SEC_Q2_SALT] = salt2
            prefs[Keys.SEC_Q2_HASH] = hash2
        }
    }

    suspend fun verifySecurityQuestions(answer1: String, answer2: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val s1 = prefs[Keys.SEC_Q1_SALT] ?: return false
        val h1 = prefs[Keys.SEC_Q1_HASH] ?: return false
        val s2 = prefs[Keys.SEC_Q2_SALT] ?: return false
        val h2 = prefs[Keys.SEC_Q2_HASH] ?: return false
        val ok1 = VaultCryptoManager.verifySecret(answer1.trim().lowercase(), s1, h1)
        val ok2 = VaultCryptoManager.verifySecret(answer2.trim().lowercase(), s2, h2)
        return ok1 && ok2
    }

    suspend fun completeOnboarding(biometricEnabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = biometricEnabled
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setPreferredAuthMethod(method: AuthMethod) {
        context.securityDataStore.edit { it[Keys.PREFERRED_AUTH] = method.name }
    }

    suspend fun setAutoLockTimer(preset: AutoLockPreset, customSeconds: Int? = null) {
        val resolvedSeconds = if (preset == AutoLockPreset.CUSTOM) {
            (customSeconds ?: 30).coerceIn(5, 3600)
        } else {
            preset.seconds
        }
        context.securityDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_PRESET] = preset.name
            prefs[Keys.AUTO_LOCK_SECONDS] = resolvedSeconds
        }
    }

    suspend fun setConnectivityLock(
        wifi: Boolean? = null,
        mobileData: Boolean? = null,
        bluetooth: Boolean? = null,
        usb: Boolean? = null,
        settingsApp: Boolean? = null,
        notificationPanel: Boolean? = null
    ) {
        context.securityDataStore.edit { prefs ->
            wifi?.let { prefs[Keys.WIFI_LOCKED] = it }
            mobileData?.let { prefs[Keys.MOBILE_DATA_LOCKED] = it }
            bluetooth?.let { prefs[Keys.BLUETOOTH_LOCKED] = it }
            usb?.let { prefs[Keys.USB_LOCKED] = it }
            settingsApp?.let { prefs[Keys.SETTINGS_LOCKED] = it }
            notificationPanel?.let { prefs[Keys.NOTIFICATION_LOCKED] = it }
        }
    }

    suspend fun setKeepUnlockedDuringPlayback(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.KEEP_UNLOCKED_PLAYBACK] = enabled }
    }

    suspend fun setBackgroundAudioPlay(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.BACKGROUND_AUDIO] = enabled }
    }

    suspend fun setFlagSecureEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.FLAG_SECURE] = enabled }
    }

    suspend fun setIntruderSelfieEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.INTRUDER_SELFIE] = enabled }
    }

    suspend fun setRandomizeKeypad(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.RANDOMIZE_KEYPAD] = enabled }
    }

    suspend fun setFakeCrashEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.FAKE_CRASH_ENABLED] = enabled }
    }

    suspend fun setPanicLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.PANIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setDisguiseMode(mode: DisguiseMode) {
        context.securityDataStore.edit { it[Keys.DISGUISE_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.securityDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentTheme(accent: AccentTheme) {
        context.securityDataStore.edit { it[Keys.ACCENT_THEME] = accent.name }
    }

    suspend fun unlockProFeatures() {
        context.securityDataStore.edit { it[Keys.PRO_UNLOCKED] = true }
    }

    suspend fun applyBackupSettings(settings: BackupSettingsRecord) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.PIN_LENGTH] = settings.pinLength
            prefs[Keys.AUTO_LOCK_SECONDS] = settings.autoLockSeconds
            prefs[Keys.WIFI_LOCKED] = settings.wifiLocked
            prefs[Keys.MOBILE_DATA_LOCKED] = settings.mobileDataLocked
            prefs[Keys.BLUETOOTH_LOCKED] = settings.bluetoothLocked
            prefs[Keys.USB_LOCKED] = settings.usbLocked
            prefs[Keys.SETTINGS_LOCKED] = settings.settingsAppLocked
            prefs[Keys.NOTIFICATION_LOCKED] = settings.notificationPanelLocked
            prefs[Keys.KEEP_UNLOCKED_PLAYBACK] = settings.keepUnlockedDuringPlayback
            prefs[Keys.BACKGROUND_AUDIO] = settings.backgroundAudioPlay
            prefs[Keys.RANDOMIZE_KEYPAD] = settings.randomizeKeypad
            prefs[Keys.FAKE_CRASH_ENABLED] = settings.fakeCrashEnabled
            prefs[Keys.PANIC_LOCK_ENABLED] = settings.panicLockEnabled
        }
    }
}
