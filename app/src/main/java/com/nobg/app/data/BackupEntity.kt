package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Snapshot of an app's original state taken right before NOBG touches it
 * for the very first time. Used by the Reset / Reset All feature.
 *
 * appOpsJson stores a simple map like:
 * {"RUN_IN_BACKGROUND":"allow","RUN_ANY_IN_BACKGROUND":"allow","POST_NOTIFICATION":"allow","START_FOREGROUND":"allow"}
 */
@Entity(tableName = "app_backup_state")
data class BackupEntity(
    @PrimaryKey val packageName: String,
    val originalEnabledState: Int, // 0 default / 3 disabled-by-user (see ShizukuManager)
    val appOpsJson: String,
    val hasBackup: Boolean = true,
    val backupTimestamp: Long = System.currentTimeMillis()
)
