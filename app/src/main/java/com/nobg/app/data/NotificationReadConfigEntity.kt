package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Chế độ đọc thông báo */
enum class NotificationReadMode {
    APP_NAME_ONLY,   // Chỉ đọc: "Thông báo từ Zalo"
    FULL_CONTENT,    // Đọc: "Thông báo từ Zalo. Tiêu đề. Nội dung..."
    SMART_CHAT,      // Đọc thông minh: "Tin nhắn từ Nguyễn Văn A trên Zalo"
    SENDER_ONLY      // Chỉ đọc tên người nhắn: "Tin nhắn từ Nguyễn Văn A"
}

/** Cấu hình đọc thông báo cho từng app */
@Entity(tableName = "notification_read_config")
data class NotificationReadConfigEntity(
    @PrimaryKey val packageName: String,
    val isEnabled: Boolean = true,
    val readMode: NotificationReadMode = NotificationReadMode.FULL_CONTENT,
    val keywordFilter: String = "" // Từ khóa cần lọc (ví dụ: "gấp, OTP, ck"), để trống là đọc tất cả
)
