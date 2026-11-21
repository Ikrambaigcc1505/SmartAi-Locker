package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AuthMethod
import com.example.data.DisguiseMode
import com.example.data.SecurityConfigState
import com.example.security.BiometricAuthHelper
import com.example.ui.components.FakeCrashOverlay
import com.example.ui.components.PatternLockPad
import com.example.ui.components.PinEntryPad
import com.example.ui.components.SmartAiShieldLogo
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    config: SecurityConfigState,
    failedAttempts: Int,
    authErrorMessage: String?,
    lockedTargetAppName: String? = null,
    onDismissFakeCrashToHome: () -> Unit = {},
    onCalculatorTrigger: suspend (String) -> Boolean = { false },
    onPinSubmit: (String, androidx.lifecycle.LifecycleOwner) -> Unit,
    onPatternSubmit: (String, androidx.lifecycle.LifecycleOwner) -> Unit,
    onBiometricUnlock: () -> Unit,
    onVerifySecurityQuestions: suspend (String, String) -> Boolean,
    onResetMasterPin: (newPin: String, pinLength: Int, newPattern: String?) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var bypassCalculatorDisguise by remember(config.disguiseMode) { mutableStateOf(false) }
    var fakeCrashBypassed by remember(lockedTargetAppName) { mutableStateOf(false) }

    // Secret Calculator launch mode when Calculator disguise is active and no external app lock is covering
    if (config.disguiseMode == DisguiseMode.CALCULATOR &&
        !bypassCalculatorDisguise &&
        lockedTargetAppName == null
    ) {
        CalculatorDisguiseScreen(
            onCheckSecretTrigger = onCalculatorTrigger,
            onFallbackToStandardLock = { bypassCalculatorDisguise = true },
            isPreviewOnly = false
        )
        return
    }

    // Optional Fake Crash cover when intercepting a locked third-party app
    if (lockedTargetAppName != null && config.fakeCrashEnabled && !fakeCrashBypassed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            FakeCrashOverlay(
                targetAppName = lockedTargetAppName,
                onDismissToHome = onDismissFakeCrashToHome,
                onRevealRealLock = { fakeCrashBypassed = true }
            )
        }
        return
    }

    var activeMethod by remember(config.preferredAuthMethod) {
        mutableStateOf(config.preferredAuthMethod)
    }
    var enteredPin by remember { mutableStateOf("") }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var keypadShuffleNonce by remember { mutableIntStateOf((System.currentTimeMillis() and 0xFFFF).toInt()) }

    val canUseBiometric = remember(config.biometricEnabled) {
        config.biometricEnabled && BiometricAuthHelper.isBiometricAvailable(context)
    }

    fun launchBiometricPrompt() {
        val host = activity ?: return
        BiometricAuthHelper.authenticate(
            activity = host,
            onSuccess = onBiometricUnlock,
            onError = { /* user can fall back to PIN/Pattern */ }
        )
    }

    LaunchedEffect(canUseBiometric, fakeCrashBypassed, bypassCalculatorDisguise) {
        if (canUseBiometric) {
            launchBiometricPrompt()
        }
    }

    LaunchedEffect(authErrorMessage, failedAttempts) {
        if (authErrorMessage != null) {
            enteredPin = ""
            if (config.randomizeKeypad) {
                keypadShuffleNonce++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmartAiShieldLogo(modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (lockedTargetAppName != null) "$lockedTargetAppName Locked" else "SmartAi Locker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lockedTargetAppName != null) {
                    "Authenticate to open $lockedTargetAppName"
                } else {
                    "Private Vault & Smart Protection"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = activeMethod == AuthMethod.PIN,
                    onClick = {
                        activeMethod = AuthMethod.PIN
                        enteredPin = ""
                        onClearError()
                    },
                    label = { Text("Numeric PIN") }
                )
                if (config.hasPattern) {
                    FilterChip(
                        selected = activeMethod == AuthMethod.PATTERN,
                        onClick = {
                            activeMethod = AuthMethod.PATTERN
                            enteredPin = ""
                            onClearError()
                        },
                        label = { Text("Pattern Lock") }
                    )
                }
            }

            if (authErrorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = authErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (activeMethod == AuthMethod.PIN) {
                PinEntryPad(
                    pinLength = config.pinLength,
                    enteredPin = enteredPin,
                    isError = authErrorMessage != null,
                    showBiometricButton = canUseBiometric,
                    randomizeKeypad = config.randomizeKeypad,
                    shuffleNonce = keypadShuffleNonce,
                    onDigitPress = { digit ->
                        onClearError()
                        if (enteredPin.length < config.pinLength) {
                            enteredPin += digit
                            if (enteredPin.length == config.pinLength) {
                                onPinSubmit(enteredPin, lifecycleOwner)
                            }
                        }
                    },
                    onBackspace = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                        }
                    },
                    onBiometricClick = { launchBiometricPrompt() }
                )
            } else {
                PatternLockPad(
                    isError = authErrorMessage != null,
                    onPatternCompleted = { drawn ->
                        onClearError()
                        onPatternSubmit(drawn, lifecycleOwner)
                    }
                )
                if (canUseBiometric) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(onClick = { launchBiometricPrompt() }) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Biometric Unlock")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { showRecoveryDialog = true },
                modifier = Modifier.testTag("forgot_pin_button")
            ) {
                Text("Forgot PIN / Pattern? Offline Recovery")
            }
        }
    }

    if (showRecoveryDialog) {
        OfflineRecoveryDialog(
            config = config,
            canUseBiometric = canUseBiometric,
            onRequestBiometricRecovery = { onVerified ->
                val host = activity
                if (host != null) {
                    BiometricAuthHelper.authenticate(
                        activity = host,
                        title = "Biometric PIN Recovery",
                        subtitle = "Verify your identity to reset Master PIN",
                        negativeButtonText = "Cancel",
                        onSuccess = onVerified,
                        onError = {}
                    )
                }
            },
            onVerifySecurityAnswers = { a1, a2, callback ->
                scope.launch {
                    val ok = onVerifySecurityQuestions(a1, a2)
                    callback(ok)
                }
            },
            onResetComplete = { newPin ->
                onResetMasterPin(newPin, config.pinLength, null)
                showRecoveryDialog = false
            },
            onDismiss = { showRecoveryDialog = false }
        )
    }
}

@Composable
private fun OfflineRecoveryDialog(
    config: SecurityConfigState,
    canUseBiometric: Boolean,
    onRequestBiometricRecovery: (onVerified: () -> Unit) -> Unit,
    onVerifySecurityAnswers: (String, String, (Boolean) -> Unit) -> Unit,
    onResetComplete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var verified by remember { mutableStateOf(false) }
    var a1 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (verified) "Set New Master PIN" else "Offline PIN Recovery") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!verified) {
                    if (canUseBiometric) {
                        OutlinedButton(
                            onClick = {
                                onRequestBiometricRecovery {
                                    verified = true
                                    errorMsg = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify with Biometrics")
                        }
                    }
                    if (config.hasSecurityQuestions) {
                        Text(
                            "Or answer your local security questions:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = a1,
                            onValueChange = { a1 = it; errorMsg = null },
                            label = { Text(config.securityQuestion1) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = a2,
                            onValueChange = { a2 = it; errorMsg = null },
                            label = { Text(config.securityQuestion2) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= config.pinLength) {
                                newPin = input
                                errorMsg = null
                            }
                        },
                        label = { Text("New ${config.pinLength}-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (!verified) {
                Button(onClick = {
                    onVerifySecurityAnswers(a1, a2) { ok ->
                        if (ok) {
                            verified = true
                            errorMsg = null
                        } else {
                            errorMsg = "Security answers do not match."
                        }
                    }
                }) {
                    Text("Verify Answers")
                }
            } else {
                Button(onClick = {
                    if (newPin.length == config.pinLength) {
                        onResetComplete(newPin)
                    } else {
                        errorMsg = "Enter exactly ${config.pinLength} digits."
                    }
                }) {
                    Text("Save PIN")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
