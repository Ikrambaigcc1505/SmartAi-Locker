package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.InstalledAppInfo
import com.example.data.SecurityConfigState
import com.example.data.SystemPermissionStatus
import com.example.security.SystemPermissionsHelper
import com.example.ui.components.GlassCard
import com.example.ui.components.PermissionDisclosureBottomSheet
import com.example.ui.components.PermissionDisclosureSpec
import com.example.ui.theme.LocalVaultColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockerScreen(
    config: SecurityConfigState,
    permissionStatus: SystemPermissionStatus,
    installedApps: List<InstalledAppInfo>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleAppLock: (InstalledAppInfo, Boolean) -> Unit,
    onToggleSettingsLock: (Boolean) -> Unit,
    onToggleNotificationLock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val vaultColors = LocalVaultColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var activeDisclosure by remember { mutableStateOf<PermissionDisclosureSpec?>(null) }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefresh()
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    val filteredApps = remember(installedApps, searchQuery, showSystemApps) {
        installedApps.filter { app ->
            val matchesFilter = showSystemApps || !app.isSystemApp || app.isLocked
            val matchesSearch = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & System Locker") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("applocker_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh permissions & apps")
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
            // Native Permission Request Handlers Card with Prominent Disclosures
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                    Text(
                        text = "Required Android Lock Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Every system permission below shows a Play-compliant local privacy disclosure before opening Android Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionActionRow(
                        title = "Usage Access",
                        subtitle = "Detects foreground app launches",
                        granted = permissionStatus.usageAccessGranted,
                        onRequest = {
                            activeDisclosure = PermissionDisclosureSpec(
                                id = "usage_stats",
                                title = "Usage Access",
                                permissionName = "android.permission.PACKAGE_USAGE_STATS",
                                purposeDescription = "SmartAI Locker checks which app package moved to the foreground so it can display your PIN/Biometric lock screen immediately when a protected app opens.",
                                privacyGuarantee = "100% On-Device: App usage history is never recorded, profiled, or sent off your device.",
                                onConfirm = { SystemPermissionsHelper.openUsageAccessSettings(context) }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionActionRow(
                        title = "Display Over Other Apps",
                        subtitle = "Displays lock screen overlay",
                        granted = permissionStatus.overlayGranted,
                        onRequest = {
                            activeDisclosure = PermissionDisclosureSpec(
                                id = "overlay",
                                title = "Display Over Other Apps",
                                permissionName = "android.permission.SYSTEM_ALERT_WINDOW",
                                purposeDescription = "Allows SmartAI Locker to place your PIN, Pattern, or Fake Crash cover over locked apps before unauthorized users can view their contents.",
                                privacyGuarantee = "Zero Network Access: Overlay windows render locally with zero screen capture or analytics.",
                                onConfirm = { SystemPermissionsHelper.openOverlaySettings(context) }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionActionRow(
                        title = "Accessibility Guard",
                        subtitle = "Instant Settings & app protection",
                        granted = permissionStatus.accessibilityEnabled,
                        onRequest = {
                            activeDisclosure = PermissionDisclosureSpec(
                                id = "accessibility",
                                title = "Accessibility Guard Service",
                                permissionName = "BIND_ACCESSIBILITY_SERVICE",
                                purposeDescription = "Detects window state changes instantly on-device so locked apps and Device Settings cannot be bypassed or force-stopped without authentication.",
                                privacyGuarantee = "Strict Boundary: SmartAI Locker reads only foreground package names—never text inputs, passwords, or screen content.",
                                onConfirm = { SystemPermissionsHelper.openAccessibilitySettings(context) }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionActionRow(
                        title = "Device Admin (Anti-Uninstall)",
                        subtitle = "Prevents forced uninstall without PIN",
                        granted = permissionStatus.deviceAdminEnabled,
                        onRequest = {
                            activeDisclosure = PermissionDisclosureSpec(
                                id = "device_admin",
                                title = "Device Admin Protection",
                                permissionName = "BIND_DEVICE_ADMIN",
                                purposeDescription = "Optional safeguard that blocks unauthorized removal of SmartAI Locker while Device Settings protection is active.",
                                privacyGuarantee = "You can deactivate Device Admin anytime from Android Security Settings or this screen.",
                                onConfirm = {
                                    deviceAdminLauncher.launch(SystemPermissionsHelper.createDeviceAdminIntent(context))
                                }
                            )
                        }
                    )
                }
            }

            // OEM Battery & Auto-Start Helper Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (permissionStatus.batteryOptimizationIgnored) {
                                vaultColors.verified
                            } else {
                                vaultColors.warning
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "OEM Battery & Auto-Start Helper",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (permissionStatus.batteryOptimizationIgnored) {
                                    "Unrestricted battery active — background guard protected"
                                } else {
                                    "Battery optimization may kill lock guard on Xiaomi, Oppo, Vivo & Samsung OEMs"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                activeDisclosure = PermissionDisclosureSpec(
                                    id = "battery",
                                    title = "Unrestricted Battery Usage",
                                    permissionName = "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                                    purposeDescription = "Prevents aggressive OEM task killers from stopping SmartAI Locker's local App Guard service after reboot or screen off.",
                                    privacyGuarantee = "Sensor & monitoring services pause automatically when idle to keep battery impact near zero.",
                                    onConfirm = { SystemPermissionsHelper.openBatteryOptimizationSettings(context) }
                                )
                            },
                            enabled = !permissionStatus.batteryOptimizationIgnored,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("battery_unrestricted_button")
                        ) {
                            Text(if (permissionStatus.batteryOptimizationIgnored) "Unrestricted" else "Ignore Optimization")
                        }
                        OutlinedButton(
                            onClick = { SystemPermissionsHelper.openOemAutoStartSettings(context) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("oem_autostart_button")
                        ) {
                            Text("OEM Auto-Start")
                        }
                    }
                }
            }

            // System Settings & Notification Panel Locks
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Core System Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock Device Settings", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Requires PIN before opening Android Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.settingsAppLocked,
                            onCheckedChange = onToggleSettingsLock
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock Notification Panel", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Blocks quick-tile changes on locked screen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.notificationPanelLocked,
                            onCheckedChange = onToggleNotificationLock
                        )
                    }
                }
            }

            // Search & Filter Apps
            item {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed apps...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showSystemApps,
                            onClick = { showSystemApps = false },
                            label = { Text("User Apps") }
                        )
                        FilterChip(
                            selected = showSystemApps,
                            onClick = { showSystemApps = true },
                            label = { Text("All Installed (${installedApps.size})") }
                        )
                    }
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                InstalledAppLockRow(
                    app = app,
                    onToggle = { locked -> onToggleAppLock(app, locked) }
                )
            }
        }
    }

    activeDisclosure?.let { spec ->
        PermissionDisclosureBottomSheet(
            spec = spec,
            onDismiss = { activeDisclosure = null }
        )
    }
}

@Composable
private fun PermissionActionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) vaultColors.verified else vaultColors.warning,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = vaultColors.verified.copy(alpha = 0.16f)
                ) {
                    Text(
                        "Granted",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.verified,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            } else {
                Button(onClick = onRequest, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Enable")
                }
            }
        }
    }
}

@Composable
private fun InstalledAppLockRow(
    app: InstalledAppInfo,
    onToggle: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppDrawableIcon(drawable = app.icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = app.isLocked,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun AppDrawableIcon(drawable: Drawable?) {
    val bmp = remember(drawable) {
        runCatching { drawable?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
