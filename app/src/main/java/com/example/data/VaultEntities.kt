package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val encryptedFileName: String,
    val originalFolderHint: String,
    val aiCategoryTag: String = "General",
    val aiConfidence: Float = 0f,
    val isSoftDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val attemptMethod: String,
    val failedAttemptCount: Int,
    val encryptedPhotoFileName: String? = null,
    val summary: String
)
