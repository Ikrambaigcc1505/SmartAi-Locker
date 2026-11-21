package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ads.AdManager
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalVaultColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyGuaranteeScreen(
    adManager: AdManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val vaultColors = LocalVaultColors.current
    var consentStatus by remember { mutableStateOf(adManager.consentReadyStatus) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security Guarantee") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back")) {
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
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = vaultColors.verified
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "100% Serverless & Offline Architecture",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "SmartAi Locker has no cloud backend, no user accounts, and no telemetry servers. All hidden photos, 4K videos, audio recordings, documents, PIN/Pattern hashes, and intruder selfies remain strictly inside your device's private sandboxed storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Cryptographic Safeguards",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    PrivacyBullet("Vault Media Encryption", "64 KB chunked AES-256-GCM Cipher streams backed by Android Keystore hardware-protected keys.")
                    PrivacyBullet("PIN & Recovery Hashing", "PBKDF2-HMAC-SHA256 (100,000 iterations) with per-secret 128-bit cryptographic salts.")
                    PrivacyBullet("Portable .smartvault Backups", "Passphrase-derived AES-256-GCM streaming ZIP archives exported directly to Downloads/SD.")
                    PrivacyBullet("Screen Capture Shield", "Dynamic FLAG_SECURE blocks screenshots and blurs recents thumbnails.")
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Permission Data Usage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    PrivacyBullet("Photo Picker & SAF", "Zero broad storage permissions; imports only user-selected files.")
                    PrivacyBullet("Usage Access & Accessibility", "Inspects only foreground package transitions locally to display lock overlays; screen text/inputs are never read or logged.")
                    PrivacyBullet("Camera", "Captures front-camera intruder photo only after 3 consecutive failed unlock attempts; encrypted immediately on-device.")
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Policy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AdMob & Google UMP Consent Readiness",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Internet access is used solely by the Google Mobile Ads SDK to serve non-intrusive ads. User Messaging Platform (UMP) consent readiness is integrated for GDPR/EEA and US state privacy compliance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Status: $consentStatus",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            adManager.requestConsentInfo(activity) { updated ->
                                consentStatus = updated
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Check Ad Consent Preferences (UMP)")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyBullet(title: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
