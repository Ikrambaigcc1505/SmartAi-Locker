package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items WHERE isSoftDeleted = 0 ORDER BY importedAt DESC")
    fun observeActiveItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE category = :category AND isSoftDeleted = 0 ORDER BY importedAt DESC")
    fun observeCategoryItems(category: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isSoftDeleted = 1 ORDER BY deletedAt DESC")
    fun observeTrashItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items")
    suspend fun getAllItemsOnce(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: VaultItemEntity)

    @Query("UPDATE vault_items SET isSoftDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteItem(id: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET isSoftDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: String)

    @Query("UPDATE vault_items SET aiCategoryTag = :tag, aiConfidence = :confidence WHERE id = :id")
    suspend fun updateAiTag(id: String, tag: String, confidence: Float)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteItemPermanently(id: String)

    @Query("SELECT * FROM locked_apps ORDER BY appName ASC")
    fun observeLockedApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps")
    suspend fun getLockedAppsOnce(): List<LockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLockedApp(app: LockedAppEntity)

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun removeLockedApp(packageName: String)

    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun observeIntruderLogs(): Flow<List<IntruderLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntruderLog(log: IntruderLogEntity)

    @Query("DELETE FROM intruder_logs WHERE id = :id")
    suspend fun deleteIntruderLog(id: String)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearAllIntruderLogs()
}
