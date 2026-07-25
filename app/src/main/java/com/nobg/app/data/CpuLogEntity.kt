package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cpu_freq_log")
data class CpuLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val freqMhz: Int,
    val isUnderclockOn: Boolean
)
