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

@Dao
interface BatteryLogDao {
    @Insert
    suspend fun insert(log: BatteryLogEntity)

    @Query("SELECT * FROM battery_log WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getLogsSince(since: Long): List<BatteryLogEntity>

    @Query("SELECT * FROM battery_log ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLog(): BatteryLogEntity?

    @Query("DELETE FROM battery_log")
    suspend fun deleteAll()
}
