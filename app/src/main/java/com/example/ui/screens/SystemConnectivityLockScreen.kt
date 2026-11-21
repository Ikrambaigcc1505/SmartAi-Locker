package com.example.ui.screens

import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SecurityConfigState
import com.example.security.SystemPermissionsHelper
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalVaultColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemConnectivityLockScreen(
    config: SecurityConfigState,
    onBack: () -> Unit,
    onUpdateLock: (
        wifi: Boolean?,
        mobileData: Boolean?,
        bluetooth: Boolean?,
        usb: Boolean?,
        settingsApp: Boolean?,
        notificationPanel: Boolean?
    ) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System & Connectivity Lock") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("connectivity_back")) {
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
                    Text(
                        text = "Hardware & Radio Access Guard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Restrict unauthorized toggling of radios, USB data ports, System Settings, and the Notification Shade. Works alongside Accessibility & Settings Lock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                ConnectivityToggleCard(
                    title = "Wi-Fi Toggle Lock",
                    description = "Require Master PIN before modifying Wi-Fi networks or hotspot",
                    icon = Icons.Default.WifiLock,
                    checked = config.wifiLocked,
                    onCheckedChange = { onUpdateLock(it, null, null, null, null, null) },
                    actionLabel = "Wi-Fi Settings",
                    onOpenSystem = {
                        SystemPermissionsHelper.openConnectivitySettings(context, Settings.ACTION_WIFI_SETTINGS)
                    }
                )
            }

            item {
                ConnectivityToggleCard(
                    title = "Mobile Data Lock",
                    description = "Prevent turning off cellular data or altering SIM/APN settings",
                    icon = Icons.Default.CellTower,
                    checked = config.mobileDataLocked,
                    onCheckedChange = { onUpdateLock(null, it, null, null, null, null) },
                    actionLabel = "Mobile Network",
                    onOpenSystem = {
                        SystemPermissionsHelper.openConnectivitySettings(
                            context,
                            Settings.ACTION_DATA_ROAMING_SETTINGS
                        )
                    }
                )
            }

            item {
                ConnectivityToggleCard(
                    title = "Bluetooth Lock",
                    description = "Guard Bluetooth pairing and radio visibility changes",
                    icon = Icons.Default.BluetoothDisabled,
                    checked = config.bluetoothLocked,
                    onCheckedChange = { onUpdateLock(null, null, it, null, null, null) },
                    actionLabel = "Bluetooth Settings",
                    onOpenSystem = {
                        SystemPermissionsHelper.openConnectivitySettings(
                            context,
                            Settings.ACTION_BLUETOOTH_SETTINGS
                        )
                    }
                )
            }

            item {
                ConnectivityToggleCard(
                    title = "USB & Data Port Lock",
                    description = "Block unauthorized USB file transfer & developer debugging access",
                    icon = Icons.Default.UsbOff,
                    checked = config.usbLocked,
                    onCheckedChange = { onUpdateLock(null, null, null, it, null, null) },
                    actionLabel = "USB / Device Settings",
                    onOpenSystem = {
                        SystemPermissionsHelper.openConnectivitySettings(
                            context,
                            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                        )
                    }
                )
            }

            item {
                ConnectivityToggleCard(
                    title = "System Settings Lock",
                    description = "Intercept com.android.settings launches with PIN/Biometric gate",
                    icon = Icons.Default.Settings,
                    checked = config.settingsAppLocked,
                    onCheckedChange = { onUpdateLock(null, null, null, null, it, null) },
                    actionLabel = "Open Accessibility",
                    onOpenSystem = { SystemPermissionsHelper.openAccessibilitySettings(context) }
                )
            }

            item {
                ConnectivityToggleCard(
                    title = "Notification Shade Guard",
                    description = "Restrict quick-settings pull-down actions while protected",
                    icon = Icons.Default.NotificationsOff,
                    checked = config.notificationPanelLocked,
                    onCheckedChange = { onUpdateLock(null, null, null, null, null, it) },
                    actionLabel = "Notification Settings",
                    onOpenSystem = {
                        SystemPermissionsHelper.openConnectivitySettings(
                            context,
                            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectivityToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    actionLabel: String,
    onOpenSystem: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else vaultColors.glassBorder.copy(alpha = 0.25f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenSystem) {
            Text(actionLabel)
        }
    }
}
