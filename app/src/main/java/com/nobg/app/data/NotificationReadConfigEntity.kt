package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Chế độ đọc thông báo */
enum class NotificationReadMode {
    APP_NAME_ONLY,   // Chỉ đọc: "Thông báo từ Zalo"
    FULL_CONTENT     // Đọc: "Thông báo từ Zalo. Tiêu đề. Nội dung..."
}

/** Cấu hình đọc thông báo cho từng app */
@Entity(tableName = "notification_read_config")
data class NotificationReadConfigEntity(
    @PrimaryKey val packageName: String,
    val isEnabled: Boolean = true,
    val readMode: NotificationReadMode = NotificationReadMode.FULL_CONTENT
)
