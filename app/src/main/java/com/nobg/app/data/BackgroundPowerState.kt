package com.nobg.app.data

enum class BackgroundPowerState(
    val label: String,
    val description: String,
    val emoji: String
) {
    RESTRICTED(
        label = "Hạn chế",
        description = "Ngăn ứng dụng hoạt động ngầm để tiết kiệm pin tối đa",
        emoji = "🔴"
    ),
    OPTIMIZED(
        label = "Tối ưu hóa",
        description = "Hệ thống tự cân bằng giữa pin và hiệu năng ngầm (Mặc định)",
        emoji = "🟡"
    ),
    UNRESTRICTED(
        label = "Không hạn chế",
        description = "Cho phép chạy ngầm liên tục, không trễ thông báo",
        emoji = "🟢"
    ),
    UNKNOWN(
        label = "Chưa xác định",
        description = "Chưa thể lấy được trạng thái pin của ứng dụng",
        emoji = "⚪"
    )
}
