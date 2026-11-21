package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.AdManager
import com.example.ads.NativeAdSlot
import com.example.data.IntruderLogEntity
import com.example.data.LockedAppEntity
import com.example.data.SecurityConfigState
import com.example.data.SmartAiClassifier
import com.example.data.SystemPermissionStatus
import com.example.data.VaultCategory
import com.example.data.VaultItemEntity
import com.example.security.PanicLockSensorManager
import com.example.ui.components.GlassCard
import com.example.ui.components.SensorMonitoringBadge
import com.example.ui.components.SmartAiShieldLogo
import com.example.ui.components.formatFileSize
import com.example.ui.theme.LocalVaultColors

private data class DashboardCategoryCardModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val icon: ImageVector,
    val accentTint: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDashboardScreen(
    config: SecurityConfigState,
    permissionStatus: SystemPermissionStatus,
    activeItems: List<VaultItemEntity>,
    trashCount: Int,
    lockedApps: List<LockedAppEntity>,
    intruderLogs: List<IntruderLogEntity>,
    isDecoySession: Boolean,
    isPanicSensorMonitoring: Boolean = false,
    adManager: AdManager,
    onOpenCategory: (VaultCategory) -> Unit,
    onOpenAppLocker: () -> Unit,
    onOpenConnectivityLock: () -> Unit,
    onOpenSmartAiIntruder: () -> Unit,
    onOpenTrashAndBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockNow: () -> Unit
) {
    val context = LocalContext.current
    val hasMotionSensor = PanicLockSensorManager.hasAccelerometer(context)

    LaunchedEffect(adManager) {
        adManager.ensureNativeAdLoaded()
    }
    DisposableEffect(adManager) {
        onDispose {
            adManager.releaseCurrentNativeAd()
        }
    }

    val vaultColors = LocalVaultColors.current
    val photosCount = activeItems.count { it.category == VaultCategory.PHOTOS.name }
    val videosCount = activeItems.count { it.category == VaultCategory.VIDEOS.name }
    val audioCount = activeItems.count { it.category == VaultCategory.AUDIO.name }
    val docsCount = activeItems.count { it.category == VaultCategory.DOCUMENTS.name }
    val lockedAppCount = lockedApps.count { it.isLocked }
    val connectivityLocksCount = listOf(
        config.wifiLocked,
        config.mobileDataLocked,
        config.bluetoothLocked,
        config.usbLocked,
        config.settingsAppLocked,
        config.notificationPanelLocked
    ).count { it }
    val totalVaultBytes = activeItems.sumOf { it.sizeBytes }
    val securityReport = SmartAiClassifier.analyzeLocalSecurityScore(
        config = config,
        permissionStatus = permissionStatus,
        intruderLogs = intruderLogs
    )
    val statusBadgeTint = when {
        securityReport.score >= 80 -> vaultColors.verified
        securityReport.score >= 55 -> vaultColors.warning
        else -> vaultColors.danger
    }

    val cards = listOf(
        DashboardCategoryCardModel(
            id = "photos",
            title = "Photos & Images",
            subtitle = "JPG, PNG, WEBP, GIF, RAW",
            badgeText = "$photosCount encrypted",
            icon = Icons.Default.PhotoLibrary,
            accentTint = MaterialTheme.colorScheme.primary,
            testTag = "card_photos",
            onClick = { onOpenCategory(VaultCategory.PHOTOS) }
        ),
        DashboardCategoryCardModel(
            id = "videos",
            title = "Videos",
            subtitle = "MP4, MKV, AVI, MOV",
            badgeText = "$videosCount encrypted",
            icon = Icons.Default.VideoLibrary,
            accentTint = MaterialTheme.colorScheme.secondary,
            testTag = "card_videos",
            onClick = { onOpenCategory(VaultCategory.VIDEOS) }
        ),
        DashboardCategoryCardModel(
            id = "audio",
            title = "Audio & Recordings",
            subtitle = "MP3, WAV, AAC, M4A",
            badgeText = "$audioCount encrypted",
            icon = Icons.Default.AudioFile,
            accentTint = vaultColors.verified,
            testTag = "card_audio",
            onClick = { onOpenCategory(VaultCategory.AUDIO) }
        ),
        DashboardCategoryCardModel(
            id = "documents",
            title = "Documents & PDFs",
            subtitle = "PDF, DOCX, XLSX, PPTX, TXT, ZIP",
            badgeText = "$docsCount encrypted",
            icon = Icons.Default.Description,
            accentTint = vaultColors.warning,
            testTag = "card_documents",
            onClick = { onOpenCategory(VaultCategory.DOCUMENTS) }
        ),
        DashboardCategoryCardModel(
            id = "app_locker",
            title = "App Locker",
            subtitle = "Installed apps, Settings & Shade",
            badgeText = "$lockedAppCount protected",
            icon = Icons.Default.Apps,
            accentTint = MaterialTheme.colorScheme.primary,
            testTag = "card_app_locker",
            onClick = onOpenAppLocker
        ),
        DashboardCategoryCardModel(
            id = "connectivity_lock",
            title = "System & Connectivity Lock",
            subtitle = "Wi-Fi, Data, Bluetooth & USB Lock",
            badgeText = "$connectivityLocksCount shields on",
            icon = Icons.Default.WifiLock,
            accentTint = MaterialTheme.colorScheme.secondary,
            testTag = "card_connectivity",
            onClick = onOpenConnectivityLock
        ),
        DashboardCategoryCardModel(
            id = "smart_ai",
            title = "AI Smart Vault & Intruder Detection",
            subtitle = "Smart Scan, Silent Selfie & Decoy PIN",
            badgeText = "${intruderLogs.size} alerts",
            icon = Icons.Default.AutoAwesome,
            accentTint = vaultColors.danger,
            testTag = "card_smart_ai",
            onClick = onOpenSmartAiIntruder
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Brand Header & Security Status Card spanning full grid width
            item(span = { GridItemSpan(columns) }) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = vaultColors.glassSurface,
                    border = BorderStroke(1.dp, vaultColors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vault_shield_hero_1795246392416),
                            contentDescription = "SmartAi Locker Security Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(if (vaultColors.isDark) 0.24f else 0.12f)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
                                        )
                                    )
                                )
                        )
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF0B1020),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            SmartAiShieldLogo(modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (isDecoySession) "SmartAi Locker (Decoy)" else "SmartAi Locker",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = statusBadgeTint.copy(alpha = 0.14f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (securityReport.score >= 80) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = statusBadgeTint,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (securityReport.score >= 80) "Vault Protected • 100% Offline"
                                                        else "Shield Active • ${securityReport.score}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = statusBadgeTint,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            SensorMonitoringBadge(
                                                isMonitoring = isPanicSensorMonitoring,
                                                isEnabled = config.panicLockEnabled,
                                                hasSensor = hasMotionSensor,
                                                onClick = onOpenSettings,
                                                modifier = Modifier.testTag("home_sensor_monitoring_badge")
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = onOpenSettings,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("header_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Open Settings"
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onLockNow,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("lock_now_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock Vault Now",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Lock", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HeroStatPill(
                                    label = "Security Score",
                                    value = "${securityReport.score}/100 • ${securityReport.grade}",
                                    modifier = Modifier.weight(1f)
                                )
                                HeroStatPill(
                                    label = "Hidden Files",
                                    value = "${activeItems.size} (${formatFileSize(totalVaultBytes)})",
                                    modifier = Modifier.weight(1f)
                                )
                                HeroStatPill(
                                    label = "Auto-Lock",
                                    value = if (config.autoLockSeconds == 0) "Instant" else "${config.autoLockSeconds}s",
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 7 Interactive Category Tab Cards
            items(cards, key = { it.id }) { card ->
                val isWideTail = card.id == "smart_ai" && columns == 2
                GlassCard(
                    onClick = card.onClick,
                    highlightBorder = card.id == "smart_ai",
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 142.dp)
                        .testTag(card.testTag)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(card.accentTint.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = card.icon,
                                contentDescription = card.title,
                                tint = card.accentTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = card.accentTint.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = card.badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = card.accentTint,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (isWideTail) 2 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Utility Strip (Trash Bin, .smartvault Backup, Settings)
            item(span = { GridItemSpan(columns) }) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Vault Tools & Recovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = onOpenTrashAndBackup,
                            label = { Text("Trash ($trashCount)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("chip_trash")
                        )
                        AssistChip(
                            onClick = onOpenTrashAndBackup,
                            label = { Text(".smartvault", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("chip_backup")
                        )
                        AssistChip(
                            onClick = onOpenSettings,
                            label = { Text("Settings", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("chip_settings")
                        )
                    }
                }
            }

            // Separated Native Advanced Ad Slot (renders cleanly when loaded, collapses cleanly when offline)
            item(span = { GridItemSpan(columns) }) {
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    NativeAdSlot(adManager = adManager)
                }
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        border = BorderStroke(0.75.dp, LocalVaultColors.current.glassBorder.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
