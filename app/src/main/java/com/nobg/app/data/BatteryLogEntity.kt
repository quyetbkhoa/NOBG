package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_log")
data class BatteryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isScreenOn: Boolean
)
