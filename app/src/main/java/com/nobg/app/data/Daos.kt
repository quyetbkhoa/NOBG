package com.nobg.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM nobg_apps")
    fun observeAll(): Flow<List<AppEntity>>

    @Query("SELECT * FROM nobg_apps WHERE packageName = :pkg")
    suspend fun get(pkg: String): AppEntity?

    @Query("SELECT * FROM nobg_apps WHERE enabled = 1")
    suspend fun getAllEnabled(): List<AppEntity>

    @Upsert
    suspend fun upsert(entity: AppEntity)

    @Query("DELETE FROM nobg_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("UPDATE nobg_apps SET blockedCount = blockedCount + 1, lastActionAt = :ts WHERE packageName = :pkg")
    suspend fun incrementBlockedCount(pkg: String, ts: Long)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM app_backup_state WHERE packageName = :pkg")
    suspend fun get(pkg: String): BackupEntity?

    @Query("SELECT * FROM app_backup_state")
    suspend fun getAll(): List<BackupEntity>

    @Upsert
    suspend fun upsert(entity: BackupEntity)

    @Query("DELETE FROM app_backup_state WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}
