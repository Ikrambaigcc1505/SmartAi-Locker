package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import com.example.data.AiScanCandidate
import com.example.data.IntruderLogEntity
import com.example.data.SecurityConfigState
import com.example.data.SmartAiClassifier
import com.example.data.SystemPermissionStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.LocalVaultColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAiIntruderScreen(
    config: SecurityConfigState,
    permissionStatus: SystemPermissionStatus,
    aiCandidates: List<AiScanCandidate>,
    intruderLogs: List<IntruderLogEntity>,
    onBack: () -> Unit,
    onScanUris: (List<Uri>) -> Unit,
    onAutoHideCandidate: (AiScanCandidate) -> Unit,
    onAutoHideAll: () -> Unit,
    onReclassifyVault: () -> Unit,
    onToggleIntruderSelfie: (Boolean) -> Unit,
    onTestIntruderSelfie: (androidx.lifecycle.LifecycleOwner) -> Unit,
    onLoadIntruderPhoto: suspend (String?) -> ByteArray?,
    onDeleteIntruderLog: (IntruderLogEntity) -> Unit,
    onClearIntruderLogs: () -> Unit,
    onSaveDecoyPin: (String, Boolean) -> Unit,
    onToggleDecoy: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit,
    onWatchRewardedUnlock: (Activity?) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val scanPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onScanUris(uris)
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshPermissions()
        if (granted) onToggleIntruderSelfie(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartAI & Intruder Guard") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("smartai_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("SmartAI Scan") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Intruders (${intruderLogs.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Decoy Vault") }
                )
            }

            when (selectedTab) {
                0 -> {
                    val securityReport = remember(config, permissionStatus, intruderLogs) {
                        SmartAiClassifier.analyzeLocalSecurityScore(
                            config = config,
                            permissionStatus = permissionStatus,
                            intruderLogs = intruderLogs
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            val vaultColors = LocalVaultColors.current
                            val scoreTint = when {
                                securityReport.score >= 85 -> vaultColors.verified
                                securityReport.score >= 60 -> vaultColors.warning
                                else -> vaultColors.danger
                            }
                            GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Local Device Security Score",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Calculated on-device from Biometrics, PIN, Auto-Lock & Logs",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = scoreTint.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            "${securityReport.score}/100 • ${securityReport.grade}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = scoreTint,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { securityReport.score / 100f },
                                    color = scoreTint,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    securityReport.checks.forEach { check ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (check.passed) vaultColors.verified else vaultColors.warning,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${check.title}: ${check.detail}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "On-Device Smart Categorizer",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "100% offline regex, MIME-type & size engine detects IDs, invoices, bank PDFs & large media.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { scanPickerLauncher.launch(arrayOf("*/*")) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ai_scan_files_button")
                                    ) {
                                        Text("Scan Files")
                                    }
                                    OutlinedButton(
                                        onClick = onReclassifyVault,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Re-Tag Vault")
                                    }
                                }
                            }
                        }

                        if (!config.proFeaturesUnlocked) {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Unlock Pro Themes & Deep AI Pack",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                "Watch a short rewarded video to unlock Emerald & Amethyst themes",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(onClick = { onWatchRewardedUnlock(activity) }) {
                                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Unlock")
                                        }
                                    }
                                }
                            }
                        }

                        val pendingCount = aiCandidates.count { !it.isAlreadyVaulted }
                        if (pendingCount > 0) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Auto-Hide Suggestions ($pendingCount)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Button(onClick = onAutoHideAll) {
                                        Text("Hide All")
                                    }
                                }
                            }
                        }

                        items(aiCandidates, key = { it.id }) { candidate ->
                            AiCandidateCard(
                                candidate = candidate,
                                onLockNow = { onAutoHideCandidate(candidate) }
                            )
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CameraFront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Silent Intruder Capture (3 Wrong Attempts)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (permissionStatus.cameraGranted) {
                                                "Zero shutter sound/flash • Saved to vault & Pictures/SmartAi Locker"
                                            } else {
                                                "Camera permission needed to capture intruder photos"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = config.intruderSelfieEnabled,
                                        onCheckedChange = { enabled ->
                                            if (enabled && !permissionStatus.cameraGranted) {
                                                cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                            } else {
                                                onToggleIntruderSelfie(enabled)
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!permissionStatus.cameraGranted) {
                                        Button(onClick = {
                                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                        }) {
                                            Text("Grant Camera")
                                        }
                                    } else {
                                        OutlinedButton(onClick = { onTestIntruderSelfie(lifecycleOwner) }) {
                                            Text("Test Capture")
                                        }
                                    }
                                    if (intruderLogs.isNotEmpty()) {
                                        TextButton(onClick = onClearIntruderLogs) {
                                            Text("Clear Logs", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        if (intruderLogs.isEmpty()) {
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "No Intruder Alerts Recorded",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Any 3 consecutive failed PIN or Pattern attempts will automatically log timestamped evidence here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(intruderLogs, key = { it.id }) { log ->
                                IntruderLogCard(
                                    log = log,
                                    onLoadPhoto = onLoadIntruderPhoto,
                                    onDelete = { onDeleteIntruderLog(log) }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    DecoyVaultConfigTab(
                        config = config,
                        onSaveDecoyPin = onSaveDecoyPin,
                        onToggleDecoy = onToggleDecoy
                    )
                }
            }
        }
    }
}

@Composable
private fun AiCandidateCard(
    candidate: AiScanCandidate,
    onLockNow: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    candidate.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            "${candidate.aiTag} • ${(candidate.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        formatFileSize(candidate.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    candidate.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (candidate.isAlreadyVaulted) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = vaultColors.verified.copy(alpha = 0.16f)
                ) {
                    Text(
                        "Locked",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.verified,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Button(onClick = onLockNow) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hide")
                }
            }
        }
    }
}

@Composable
private fun IntruderLogCard(
    log: IntruderLogEntity,
    onLoadPhoto: suspend (String?) -> ByteArray?,
    onDelete: () -> Unit
) {
    var photoBytes by remember(log.id) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(log.encryptedPhotoFileName) {
        photoBytes = onLoadPhoto(log.encryptedPhotoFileName)
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(photoBytes) {
                photoBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Captured intruder selfie",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (log.encryptedPhotoFileName != null) "Selfie encrypted in sandbox" else "No camera photo captured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete log")
            }
        }
    }
}

@Composable
private fun DecoyVaultConfigTab(
    config: SecurityConfigState,
    onSaveDecoyPin: (String, Boolean) -> Unit,
    onToggleDecoy: (Boolean) -> Unit
) {
    var fakePin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    LazyColumn(
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Decoy Vault Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Unlocking with your Decoy PIN opens an isolated empty vault with zero hidden files or intruder logs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.decoyEnabled,
                        onCheckedChange = onToggleDecoy,
                        enabled = config.hasDecoyPin
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = fakePin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= config.pinLength) {
                            fakePin = input
                            err = null
                        }
                    },
                    label = {
                        Text(
                            if (config.hasDecoyPin) "Update Decoy PIN (${config.pinLength} digits)"
                            else "Create Decoy PIN (${config.pinLength} digits)"
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (fakePin.length != config.pinLength) {
                            err = "Enter exactly ${config.pinLength} digits."
                        } else {
                            onSaveDecoyPin(fakePin, true)
                            fakePin = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Enable Decoy PIN")
                }
            }
        }
    }
}
