package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.AccentTheme
import com.example.data.AuthMethod
import com.example.data.AutoLockPreset
import com.example.data.SecurityConfigState
import com.example.data.ThemeMode
import com.example.security.BiometricAuthHelper
import com.example.security.PanicLockSensorManager
import com.example.ui.components.GlassCard
import com.example.ui.components.PatternLockPad
import com.example.ui.components.SensorMonitoringStatusBanner
import com.example.ui.theme.LocalVaultColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: SecurityConfigState,
    isPanicSensorMonitoring: Boolean = false,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentTheme: (AccentTheme) -> Unit,
    onSetPreferredAuth: (AuthMethod) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onSetIntruderSelfieEnabled: (Boolean) -> Unit,
    onSetAutoLockTimer: (AutoLockPreset, Int?) -> Unit,
    onSetKeepUnlockedDuringPlayback: (Boolean) -> Unit,
    onSetBackgroundAudioPlay: (Boolean) -> Unit,
    onSetFlagSecure: (Boolean) -> Unit,
    onSetRandomizeKeypad: (Boolean) -> Unit,
    onSetFakeCrashEnabled: (Boolean) -> Unit,
    onSetPanicLockEnabled: (Boolean) -> Unit,
    onOpenAppDisguise: () -> Unit,
    onOpenPrivacyGuarantee: () -> Unit,
    onVerifyCurrentPin: suspend (String) -> Boolean,
    onUpdatePinAndPattern: (newPin: String, pinLength: Int, newPattern: String?) -> Unit,
    onUpdateSecurityQuestions: (String, String, String, String) -> Unit,
    onWatchRewardedUnlock: (Activity?) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val vaultColors = LocalVaultColors.current
    val biometricReady = remember { BiometricAuthHelper.isBiometricAvailable(context) }
    val accelerometerReady = remember { PanicLockSensorManager.hasAccelerometer(context) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) {
            onSetIntruderSelfieEnabled(true)
        }
    }

    var showCustomTimerDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showChangePatternDialog by remember { mutableStateOf(false) }
    var showQuestionsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Vault Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Auto-Lock Timer Settings
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Auto-Lock Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (config.autoLockSeconds == 0) "Instant lock on minimize/screen off"
                                else "Locks after ${config.autoLockSeconds}s in background",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AutoLockPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = config.autoLockPreset == preset,
                                onClick = {
                                    if (preset == AutoLockPreset.CUSTOM) {
                                        showCustomTimerDialog = true
                                    } else {
                                        onSetAutoLockTimer(preset, null)
                                    }
                                },
                                label = {
                                    if (preset == AutoLockPreset.CUSTOM && config.autoLockPreset == AutoLockPreset.CUSTOM) {
                                        Text("Custom (${config.autoLockSeconds}s)")
                                    } else {
                                        Text(preset.label)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Authentication Methods, Credentials & Silent Intruder Capture
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Authentication & Intruder Protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Primary Unlock Method", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthMethod.entries.forEach { method ->
                            FilterChip(
                                selected = config.preferredAuthMethod == method,
                                onClick = { onSetPreferredAuth(method) },
                                label = { Text(method.label) },
                                modifier = Modifier.testTag("settings_auth_method_${method.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${config.pinLength}-Digit PIN Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = (if (config.hasPattern) vaultColors.verified else vaultColors.warning).copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = if (config.hasPattern) "Pattern Lock Set" else "Pattern Not Set",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (config.hasPattern) vaultColors.verified else vaultColors.warning,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fingerprint / Biometric Unlock", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (biometricReady) {
                                    if (config.biometricEnabled) "Enabled for vault & locked apps"
                                    else "Device biometrics available — toggle to enable"
                                } else "Enroll Fingerprint or Face in Android Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.biometricEnabled,
                            onCheckedChange = onSetBiometricEnabled,
                            enabled = biometricReady,
                            modifier = Modifier.testTag("settings_biometric_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraFront, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Silent Intruder Capture (3 Wrong Tries)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (cameraGranted) "Captures silently with zero sound/flash & saves to Pictures/SmartAi Locker"
                                else "Requires Camera permission for silent front capture",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.intruderSelfieEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !cameraGranted) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    onSetIntruderSelfieEnabled(enabled)
                                }
                            },
                            modifier = Modifier.testTag("settings_intruder_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showChangePinDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_change_pin_button")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change PIN")
                        }
                        OutlinedButton(
                            onClick = { showChangePatternDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_change_pattern_button")
                        ) {
                            Icon(Icons.Default.Pattern, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (config.hasPattern) "Change Pattern" else "Set Pattern")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showQuestionsDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Edit Local Security Questions")
                    }
                }
            }

            // Media Playback & Anti-Capture Toggles
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Media & Privacy Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Keep Unlocked During Video/Audio Playback",
                        subtitle = "Prevents auto-lock while watching encrypted media",
                        checked = config.keepUnlockedDuringPlayback,
                        onCheckedChange = onSetKeepUnlockedDuringPlayback
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Background Audio Playback",
                        subtitle = "Continue audio playback when minimizing player",
                        checked = config.backgroundAudioPlay,
                        onCheckedChange = onSetBackgroundAudioPlay
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Screenshot & Recents Shield (FLAG_SECURE)",
                        subtitle = "Blocks screenshots & blurs app preview in recent tasks",
                        checked = config.flagSecureEnabled,
                        onCheckedChange = onSetFlagSecure
                    )
                }
            }

            // Stealth Disguise, Randomized Keypad, Fake Crash & Panic Lock
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Stealth, Disguise & Panic Lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenAppDisguise,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_app_disguise_button")
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "App Disguise (${config.disguiseMode.launcherLabel})",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingSwitchRow(
                        title = "Randomized 0–9 PIN Keypad",
                        subtitle = "Shuffles keypad numbers on every unlock prompt",
                        checked = config.randomizeKeypad,
                        onCheckedChange = onSetRandomizeKeypad
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Fake Crash Cover Dialog",
                        subtitle = "Shows 'App stopped' overlay; long-press OK to unlock",
                        checked = config.fakeCrashEnabled,
                        onCheckedChange = onSetFakeCrashEnabled
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow(
                        title = "Panic Gesture",
                        subtitle = if (accelerometerReady) {
                            "Enables SensorManager detection for face-down flip or firm shake to lock & wipe session"
                        } else {
                            "Accelerometer sensor unavailable on this device"
                        },
                        checked = config.panicLockEnabled,
                        onCheckedChange = onSetPanicLockEnabled,
                        enabled = accelerometerReady || config.panicLockEnabled,
                        switchTestTag = "switch_panic_lock"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SensorMonitoringStatusBanner(
                        isMonitoring = isPanicSensorMonitoring,
                        isEnabled = config.panicLockEnabled,
                        hasSensor = accelerometerReady,
                        modifier = Modifier.testTag("panic_sensor_status_indicator")
                    )
                }
            }

            // Offline Privacy Policy & AdMob Consent Readiness
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_privacy_guarantee_card"),
                    onClick = onOpenPrivacyGuarantee
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Policy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Privacy & Security Guarantee",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "100% offline encryption policy, permission disclosures & UMP consent",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            // Theme & Pro Accent Colors
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Appearance & Vault Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Accent Shield Color", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentTheme.entries.forEach { accent ->
                            val locked = accent.isPro && !config.proFeaturesUnlocked
                            FilterChip(
                                selected = config.accentTheme == accent,
                                onClick = {
                                    if (locked) {
                                        onWatchRewardedUnlock(activity)
                                    } else {
                                        onSetAccentTheme(accent)
                                    }
                                },
                                label = {
                                    Text(if (locked) "${accent.label} (Pro)" else accent.label)
                                }
                            )
                        }
                    }
                    if (!config.proFeaturesUnlocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onWatchRewardedUnlock(activity) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watch Rewarded Ad to Unlock Pro Themes")
                        }
                    }
                }
            }
        }
    }

    if (showCustomTimerDialog) {
        CustomTimerDialog(
            initialSeconds = config.autoLockSeconds,
            onSave = { totalSeconds ->
                onSetAutoLockTimer(AutoLockPreset.CUSTOM, totalSeconds)
                showCustomTimerDialog = false
            },
            onDismiss = { showCustomTimerDialog = false }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            currentPinLength = config.pinLength,
            onVerifyCurrent = { pin, result ->
                scope.launch { result(onVerifyCurrentPin(pin)) }
            },
            onSaveNewPin = { newPin, newLen ->
                onUpdatePinAndPattern(newPin, newLen, null)
                showChangePinDialog = false
            },
            onDismiss = { showChangePinDialog = false }
        )
    }

    if (showChangePatternDialog) {
        ChangePatternDialog(
            onSavePattern = { pattern ->
                showChangePatternDialog = false
                onUpdatePinAndPattern("", config.pinLength, pattern)
            },
            onDismiss = { showChangePatternDialog = false }
        )
    }

    if (showQuestionsDialog) {
        EditSecurityQuestionsDialog(
            initialQ1 = config.securityQuestion1,
            initialQ2 = config.securityQuestion2,
            onSave = { q1, a1, q2, a2 ->
                onUpdateSecurityQuestions(q1, a1, q2, a2)
                showQuestionsDialog = false
            },
            onDismiss = { showQuestionsDialog = false }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    switchTestTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier
        )
    }
}

@Composable
private fun CustomTimerDialog(
    initialSeconds: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var minutesInput by remember { mutableStateOf((initialSeconds / 60).toString()) }
    var secondsInput by remember { mutableStateOf((initialSeconds % 60).coerceAtLeast(10).toString()) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Auto-Lock Timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter manual minutes and seconds before locking after minimize.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { if (it.all { c -> c.isDigit() }) minutesInput = it },
                        label = { Text("Minutes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = secondsInput,
                        onValueChange = { if (it.all { c -> c.isDigit() }) secondsInput = it },
                        label = { Text("Seconds") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val mins = minutesInput.toIntOrNull() ?: 0
                val secs = secondsInput.toIntOrNull() ?: 0
                val total = mins * 60 + secs
                if (total < 5 || total > 3600) {
                    err = "Enter between 5 seconds and 60 minutes."
                } else {
                    onSave(total)
                }
            }) {
                Text("Apply Timer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChangePinDialog(
    currentPinLength: Int,
    onVerifyCurrent: (String, (Boolean) -> Unit) -> Unit,
    onSaveNewPin: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var targetLength by remember { mutableIntStateOf(currentPinLength) }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (verified) "Set New Master PIN" else "Verify Current PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!verified) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= currentPinLength) {
                                currentPin = input
                                error = null
                            }
                        },
                        label = { Text("Current $currentPinLength-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = targetLength == 4,
                            onClick = {
                                targetLength = 4
                                newPin = ""
                                confirmNewPin = ""
                                error = null
                            },
                            label = { Text("4-Digit") }
                        )
                        FilterChip(
                            selected = targetLength == 6,
                            onClick = {
                                targetLength = 6
                                newPin = ""
                                confirmNewPin = ""
                                error = null
                            },
                            label = { Text("6-Digit") }
                        )
                    }
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= targetLength) {
                                newPin = input
                                error = null
                            }
                        },
                        label = { Text("New $targetLength-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmNewPin,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= targetLength) {
                                confirmNewPin = input
                                error = null
                            }
                        },
                        label = { Text("Confirm $targetLength-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!verified) {
                Button(onClick = {
                    onVerifyCurrent(currentPin) { ok ->
                        if (ok) {
                            verified = true
                            error = null
                        } else {
                            error = "Incorrect current PIN."
                        }
                    }
                }) {
                    Text("Verify")
                }
            } else {
                Button(onClick = {
                    when {
                        newPin.length != targetLength -> error = "Enter exactly $targetLength digits."
                        confirmNewPin != newPin -> error = "PINs did not match. Try again."
                        else -> onSaveNewPin(newPin, targetLength)
                    }
                }) {
                    Text("Save PIN")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChangePatternDialog(
    onSavePattern: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var firstPattern by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isConfirming) "Confirm New Pattern" else "Draw New Pattern") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isConfirming) "Draw the same pattern again to confirm"
                    else "Connect at least 4 dots",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                PatternLockPad(
                    isError = error != null,
                    onPatternCompleted = { drawn ->
                        error = null
                        if (drawn.length < 4) {
                            error = "Connect at least 4 dots."
                        } else if (!isConfirming) {
                            firstPattern = drawn
                            isConfirming = true
                        } else if (drawn == firstPattern) {
                            onSavePattern(drawn)
                        } else {
                            error = "Patterns did not match. Draw again."
                        }
                    }
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (isConfirming) {
                TextButton(onClick = {
                    isConfirming = false
                    firstPattern = ""
                    error = null
                }) {
                    Text("Redraw")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditSecurityQuestionsDialog(
    initialQ1: String,
    initialQ2: String,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var q1 by remember { mutableStateOf(initialQ1) }
    var a1 by remember { mutableStateOf("") }
    var q2 by remember { mutableStateOf(initialQ2) }
    var a2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Security Questions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = q1, onValueChange = { q1 = it }, label = { Text("Question 1") })
                OutlinedTextField(value = a1, onValueChange = { a1 = it; error = null }, label = { Text("New Answer 1") })
                OutlinedTextField(value = q2, onValueChange = { q2 = it }, label = { Text("Question 2") })
                OutlinedTextField(value = a2, onValueChange = { a2 = it; error = null }, label = { Text("New Answer 2") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (a1.trim().length < 2 || a2.trim().length < 2) {
                    error = "Please enter both answers."
                } else {
                    onSave(q1, a1, q2, a2)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
