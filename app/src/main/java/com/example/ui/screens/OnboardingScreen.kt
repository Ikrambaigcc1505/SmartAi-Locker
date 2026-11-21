package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.security.BiometricAuthHelper
import com.example.ui.components.GlassCard
import com.example.ui.components.PatternLockPad
import com.example.ui.components.PinEntryPad
import com.example.ui.components.SmartAiShieldLogo

@Composable
fun OnboardingScreen(
    onComplete: (
        pin: String,
        pinLength: Int,
        pattern: String,
        q1: String,
        a1: String,
        q2: String,
        a2: String,
        biometric: Boolean,
        decoyPin: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val biometricSupported = remember { BiometricAuthHelper.isBiometricAvailable(context) }

    var step by remember { mutableIntStateOf(0) }
    var pinLength by remember { mutableIntStateOf(4) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmingPin by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    var firstPattern by remember { mutableStateOf("") }
    var isConfirmingPattern by remember { mutableStateOf(false) }
    var patternError by remember { mutableStateOf<String?>(null) }

    var question1 by remember { mutableStateOf("What was your childhood nickname?") }
    var answer1 by remember { mutableStateOf("") }
    var question2 by remember { mutableStateOf("What city were you born in?") }
    var answer2 by remember { mutableStateOf("") }
    var decoyPin by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    var biometricEnabled by remember { mutableStateOf(biometricSupported) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 540.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SmartAiShieldLogo(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SmartAi Locker Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Step ${step + 1} of 4 • 100% Local Encrypted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (step + 1) / 4f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(22.dp))

            AnimatedContent(targetState = step, label = "onboardingStep") { currentStep ->
                when (currentStep) {
                    0 -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isConfirmingPin) "Confirm Master PIN" else "Create Master PIN",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Choose 4-digit or 6-digit PIN to encrypt your local vault.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (!isConfirmingPin) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilterChip(
                                        selected = pinLength == 4,
                                        onClick = {
                                            pinLength = 4
                                            firstPin = ""
                                            pinError = null
                                        },
                                        label = { Text("4-Digit PIN") }
                                    )
                                    FilterChip(
                                        selected = pinLength == 6,
                                        onClick = {
                                            pinLength = 6
                                            firstPin = ""
                                            pinError = null
                                        },
                                        label = { Text("6-Digit PIN") }
                                    )
                                }
                            }
                            pinError?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val activePin = if (isConfirmingPin) confirmPin else firstPin
                            PinEntryPad(
                                pinLength = pinLength,
                                enteredPin = activePin,
                                isError = pinError != null,
                                showBiometricButton = false,
                                onDigitPress = { digit ->
                                    pinError = null
                                    if (!isConfirmingPin) {
                                        if (firstPin.length < pinLength) {
                                            firstPin += digit
                                            if (firstPin.length == pinLength) {
                                                isConfirmingPin = true
                                            }
                                        }
                                    } else {
                                        if (confirmPin.length < pinLength) {
                                            confirmPin += digit
                                            if (confirmPin.length == pinLength) {
                                                if (confirmPin == firstPin) {
                                                    step = 1
                                                } else {
                                                    pinError = "PINs did not match. Try again."
                                                    confirmPin = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                onBackspace = {
                                    pinError = null
                                    if (!isConfirmingPin && firstPin.isNotEmpty()) {
                                        firstPin = firstPin.dropLast(1)
                                    } else if (isConfirmingPin && confirmPin.isNotEmpty()) {
                                        confirmPin = confirmPin.dropLast(1)
                                    }
                                },
                                onBiometricClick = {}
                            )
                            if (isConfirmingPin) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = {
                                    isConfirmingPin = false
                                    firstPin = ""
                                    confirmPin = ""
                                    pinError = null
                                }) {
                                    Text("Start Over")
                                }
                            }
                        }
                    }

                    1 -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Pattern,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isConfirmingPattern) "Draw Pattern Again" else "Create Pattern Lock",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Connect at least 4 dots for quick gesture unlock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            patternError?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            PatternLockPad(
                                isError = patternError != null,
                                onPatternCompleted = { drawn ->
                                    patternError = null
                                    if (drawn.length < 4) {
                                        patternError = "Connect at least 4 dots."
                                    } else if (!isConfirmingPattern) {
                                        firstPattern = drawn
                                        isConfirmingPattern = true
                                    } else {
                                        if (drawn == firstPattern) {
                                            step = 2
                                        } else {
                                            patternError = "Pattern did not match. Draw again."
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { step = 0 }) {
                                    Text("Back")
                                }
                                if (isConfirmingPattern) {
                                    TextButton(onClick = {
                                        isConfirmingPattern = false
                                        firstPattern = ""
                                        patternError = null
                                    }) {
                                        Text("Redraw")
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Offline Recovery & Decoy Vault",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Security questions let you reset a forgotten PIN completely offline without any cloud server.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = question1,
                                onValueChange = { question1 = it },
                                label = { Text("Security Question 1") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = answer1,
                                onValueChange = {
                                    answer1 = it
                                    recoveryError = null
                                },
                                label = { Text("Answer 1") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_answer_1")
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = question2,
                                onValueChange = { question2 = it },
                                label = { Text("Security Question 2") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = answer2,
                                onValueChange = {
                                    answer2 = it
                                    recoveryError = null
                                },
                                label = { Text("Answer 2") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_answer_2")
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = decoyPin,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= pinLength) {
                                        decoyPin = input
                                    }
                                },
                                label = { Text("Optional Decoy Fake PIN ($pinLength digits)") },
                                supportingText = { Text("Opens an empty decoy vault if coerced to unlock") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            recoveryError?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = { step = 1 }) {
                                    Text("Back")
                                }
                                Button(
                                    onClick = {
                                        when {
                                            answer1.trim().length < 2 || answer2.trim().length < 2 -> {
                                                recoveryError = "Please enter both security answers."
                                            }
                                            decoyPin.isNotEmpty() && (decoyPin.length != pinLength || decoyPin == firstPin) -> {
                                                recoveryError = "Decoy PIN must be $pinLength digits and differ from Master PIN."
                                            }
                                            else -> step = 3
                                        }
                                    },
                                    modifier = Modifier.testTag("onboarding_next_bio")
                                ) {
                                    Text("Continue")
                                }
                            }
                        }
                    }

                    3 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Biometric Unlock",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (biometricSupported) {
                                                "Fingerprint / Face biometric detected on device"
                                            } else {
                                                "Enroll device biometrics in Android Settings to activate"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = biometricEnabled,
                                        onCheckedChange = { biometricEnabled = it },
                                        enabled = biometricSupported
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Security Summary", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("• $pinLength-Digit PBKDF2-SHA256 Master PIN", style = MaterialTheme.typography.bodyMedium)
                                Text("• 3x3 Pattern Gesture Lock", style = MaterialTheme.typography.bodyMedium)
                                Text("• 2 Offline Recovery Questions", style = MaterialTheme.typography.bodyMedium)
                                Text("• AES-256-GCM Sandboxed Media Encryption", style = MaterialTheme.typography.bodyMedium)
                                if (decoyPin.isNotEmpty()) {
                                    Text("• Decoy Vault Fake PIN Active", style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = { step = 2 }) {
                                    Text("Back")
                                }
                                Button(
                                    onClick = {
                                        onComplete(
                                            firstPin,
                                            pinLength,
                                            firstPattern,
                                            question1,
                                            answer1,
                                            question2,
                                            answer2,
                                            biometricEnabled,
                                            decoyPin.takeIf { it.isNotEmpty() }
                                        )
                                    },
                                    modifier = Modifier.testTag("onboarding_finish_button")
                                ) {
                                    Text("Activate Vault")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
