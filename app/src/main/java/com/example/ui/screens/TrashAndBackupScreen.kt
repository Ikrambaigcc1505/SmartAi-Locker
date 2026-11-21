package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.VaultItemEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashAndBackupScreen(
    trashItems: List<VaultItemEntity>,
    onBack: () -> Unit,
    onExportBackup: (passphrase: String, customUri: Uri?) -> Unit,
    onImportBackup: (uri: Uri, passphrase: String) -> Unit,
    onRestoreFromTrash: (VaultItemEntity) -> Unit,
    onPermanentDelete: (VaultItemEntity) -> Unit
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingCustomExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var itemToPermDelete by remember { mutableStateOf<VaultItemEntity?>(null) }

    val createBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingCustomExportUri = uri
            showExportDialog = true
        }
    }

    val openBackupDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash & .smartvault Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("trash_backup_back")) {
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
            // Encrypted Local Backup & Restore Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), highlightBorder = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AES-256 Encrypted Local Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Export or restore all hidden media, locked apps, and settings in a single passphrase-protected .smartvault file saved directly to Downloads or SD storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                pendingCustomExportUri = null
                                showExportDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_downloads_button")
                        ) {
                            Text("Save to Downloads")
                        }
                        OutlinedButton(
                            onClick = { openBackupDocLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_backup_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            createBackupDocLauncher.launch("SmartAI_Backup.smartvault")
                        }
                    ) {
                        Text("Or choose custom SD / folder location...")
                    }
                }
            }

            // Soft-Deleted Trash Bin Section
            item {
                Text(
                    text = "Soft-Deleted Trash Bin (${trashItems.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (trashItems.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Trash Bin is Empty",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Files removed with 'Move to Trash (Soft Delete)' stay safely recoverable here until permanently erased.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(trashItems, key = { it.id }) { item ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.originalName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(item.sizeBytes)} • Deleted ${formatTimestamp(item.deletedAt ?: item.importedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRestoreFromTrash(item) }) {
                                Icon(
                                    Icons.Default.RestoreFromTrash,
                                    contentDescription = "Restore to vault",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { itemToPermDelete = item }) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Permanently erase",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        PassphraseDialog(
            title = "Encrypt .smartvault Backup",
            subtitle = "Enter a passphrase (4+ chars) to encrypt your backup with PBKDF2 + AES-256-GCM.",
            confirmLabel = "Create Backup",
            onConfirm = { pass ->
                onExportBackup(pass, pendingCustomExportUri)
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false }
        )
    }

    pendingImportUri?.let { importUri ->
        PassphraseDialog(
            title = "Decrypt .smartvault Backup",
            subtitle = "Enter the backup passphrase to restore your encrypted files and settings.",
            confirmLabel = "Restore Backup",
            onConfirm = { pass ->
                onImportBackup(importUri, pass)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null }
        )
    }

    itemToPermDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToPermDelete = null },
            title = { Text("Permanently Erase File?") },
            text = {
                Text("This will irreversibly erase \"${target.originalName}\" from your device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentDelete(target)
                        itemToPermDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Erase Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToPermDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text("Backup Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (passphrase.length < 4) {
                    error = "Passphrase must be at least 4 characters."
                } else {
                    onConfirm(passphrase)
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
