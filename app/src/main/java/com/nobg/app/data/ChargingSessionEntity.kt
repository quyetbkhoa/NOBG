package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class ChargingPoint(
    val batteryPct: Int,
    val timestampMs: Long
)

@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val totalDurationSeconds: Long,
    val isCompletedToFull: Boolean,
    val pointsJson: String // Serialized List<ChargingPoint>
)
