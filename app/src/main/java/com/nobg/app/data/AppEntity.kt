package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NobgMode { STANDARD, AGGRESSIVE, DISABLE_ENABLE }

@Entity(tableName = "nobg_apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val mode: NobgMode = NobgMode.STANDARD,
    val enabled: Boolean = false,
    val delaySeconds: Int = 30, // used only for AGGRESSIVE, 10..1200 (20 min)
    val addedAt: Long = System.currentTimeMillis(),
    val blockedCount: Int = 0,
    val lastActionAt: Long = 0L
)
