package com.nobg.app.data

enum class SmartTimerMode {
    ELAPSED_TIME, // Thức đếm: "1 phút", "2 phút", "15 phút"...
    CLOCK_TIME    // Giờ thực: "Tám giờ không một", "Tám giờ hai mươi"...
}

data class SmartTimerConfig(
    val isRunning: Boolean = false,
    val mode: SmartTimerMode = SmartTimerMode.CLOCK_TIME,
    val intervalMinutes: Int = 2,
    val durationMinutes: Int = 60,
    val autoShutdown: Boolean = false,
    val volume: Float = 1.0f,
    val audioDucking: Boolean = true,
    val speechRate: Float = 1.1f,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L
)
