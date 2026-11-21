package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.VaultCategory
import com.example.data.VaultItemEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultCategoryScreen(
    category: VaultCategory,
    items: List<VaultItemEntity>,
    onBack: () -> Unit,
    onImportUris: (List<Uri>) -> Unit,
    onOpenItem: (VaultItemEntity) -> Unit,
    onUnhideItem: (VaultItemEntity) -> Unit,
    onSoftDeleteItem: (VaultItemEntity) -> Unit,
    onPermanentDeleteItem: (VaultItemEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var itemToPermDelete by remember { mutableStateOf<VaultItemEntity?>(null) }

    // Zero-permission Photo Picker for Photos & Videos, SAF OpenMultipleDocuments for Audio/Documents
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    fun launchImportPicker() {
        when (category) {
            VaultCategory.PHOTOS -> {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            VaultCategory.VIDEOS -> {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
            VaultCategory.AUDIO -> {
                documentPickerLauncher.launch(arrayOf("audio/*"))
            }
            VaultCategory.DOCUMENTS -> {
                documentPickerLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter {
            it.originalName.contains(searchQuery, ignoreCase = true) ||
                it.aiCategoryTag.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(category.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            category.supportedFormats,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("category_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { launchImportPicker() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("vault_import_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import & Hide Files")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search encrypted ${category.title.lowercase()}...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_vault_1795246391687),
                            contentDescription = "Empty encrypted vault",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 210.dp, height = 156.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Hidden ${category.title} Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap + below to import & encrypt files inside your private AES-256 sandbox.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        VaultFileCard(
                            category = category,
                            item = item,
                            onOpen = { onOpenItem(item) },
                            onUnhide = { onUnhideItem(item) },
                            onSoftDelete = { onSoftDeleteItem(item) },
                            onRequestPermanentDelete = { itemToPermDelete = item }
                        )
                    }
                }
            }
        }
    }

    itemToPermDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToPermDelete = null },
            title = { Text("Permanently Erase File?") },
            text = {
                Text(
                    "This will irreversibly wipe \"${target.originalName}\" from both SmartAI Locker and internal storage. You cannot undo this action."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentDeleteItem(target)
                        itemToPermDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Erase Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToPermDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VaultFileCard(
    category: VaultCategory,
    item: VaultItemEntity,
    onOpen: () -> Unit,
    onUnhide: () -> Unit,
    onSoftDelete: () -> Unit,
    onRequestPermanentDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val icon = when (category) {
        VaultCategory.PHOTOS -> Icons.Default.Photo
        VaultCategory.VIDEOS -> Icons.Default.VideoFile
        VaultCategory.AUDIO -> Icons.Default.AudioFile
        VaultCategory.DOCUMENTS -> Icons.Default.Description
    }

    GlassCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatFileSize(item.sizeBytes)} • ${formatTimestamp(item.importedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = item.aiCategoryTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "File options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Unhide & Restore to Device") },
                        leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onUnhide()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Trash (Soft Delete)") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSoftDelete()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestPermanentDelete()
                        }
                    )
                }
            }
        }
    }
}
