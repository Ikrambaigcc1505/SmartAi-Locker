package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DisguiseMode
import com.example.data.SecurityConfigState
import com.example.ui.components.FakeCrashOverlay
import com.example.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDisguiseScreen(
    config: SecurityConfigState,
    onBack: () -> Unit,
    onSelectDisguiseMode: (DisguiseMode) -> Unit,
    onSetRandomizeKeypad: (Boolean) -> Unit,
    onSetFakeCrashEnabled: (Boolean) -> Unit
) {
    var showCalculatorPreview by remember { mutableStateOf(false) }
    var showFakeCrashPreview by remember { mutableStateOf(false) }

    if (showCalculatorPreview) {
        CalculatorDisguiseScreen(
            onCheckSecretTrigger = { false },
            onFallbackToStandardLock = { showCalculatorPreview = false },
            isPreviewOnly = true
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("App Disguise & Stealth") },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("disguise_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Launcher Icon & App Name Disguise",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Replace the SmartAi Locker home-screen icon and title using Android activity aliases. In Calculator mode, opening the app displays a working scientific calculator; type your PIN + '=' to enter the vault.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(DisguiseMode.entries) { mode ->
                    val selected = config.disguiseMode == mode
                    val iconRes = when (mode) {
                        DisguiseMode.DEFAULT -> R.drawable.smartai_locker_icon_1795263469490
                        DisguiseMode.CALCULATOR -> R.drawable.ic_disguise_calc
                        DisguiseMode.NOTES -> R.drawable.ic_disguise_notes
                    }
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disguise_option_${mode.name}"),
                        highlightBorder = selected,
                        onClick = { onSelectDisguiseMode(mode) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = mode.launcherLabel,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (selected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Launcher Label: ${mode.launcherLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectDisguiseMode(mode) }
                            )
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { showCalculatorPreview = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_calculator_button")
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Working Secret Calculator Preview")
                    }
                }

                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Stealth Lock Overlays",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Dialpad,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Randomized PIN Keypad", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Shuffles 0–9 keys on every unlock prompt to defeat shoulder surfing",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.randomizeKeypad,
                                onCheckedChange = onSetRandomizeKeypad,
                                modifier = Modifier.testTag("switch_randomize_keypad")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fake Crash Cover Screen", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Shows 'App has stopped' on locked apps. Long-press 'OK' to reveal PIN/Biometrics",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.fakeCrashEnabled,
                                onCheckedChange = onSetFakeCrashEnabled,
                                modifier = Modifier.testTag("switch_fake_crash")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showFakeCrashPreview = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_fake_crash_button")
                        ) {
                            Text("Test Fake Crash Dialog (Long-Press OK)")
                        }
                    }
                }
            }
        }

        if (showFakeCrashPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
            ) {
                FakeCrashOverlay(
                    targetAppName = "Protected App",
                    onDismissToHome = { showFakeCrashPreview = false },
                    onRevealRealLock = { showFakeCrashPreview = false }
                )
            }
        }
    }
}
