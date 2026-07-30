package com.nobg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Thiết bị Bluetooth được chọn cho tính năng Đọc thông báo */
@Entity(tableName = "selected_bluetooth_devices")
data class SelectedBluetoothDeviceEntity(
    @PrimaryKey val address: String,     // MAC Address (VD: "AA:BB:CC:DD:EE:FF")
    val name: String,                     // Tên thiết bị (VD: "Galaxy Buds2")
    val isSelected: Boolean = true        // Đã tích chọn cho phép đọc
)
