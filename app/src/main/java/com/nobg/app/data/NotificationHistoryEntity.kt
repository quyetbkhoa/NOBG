package com.nobg.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_history",
    indices = [
        Index(value = ["postedAt"]),
        Index(value = ["packageName", "userId"])
    ]
)
data class NotificationHistoryEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val userId: Int,
    val appLabel: String,
    val title: String,
    val content: String,
    val postedAt: Long,
    val isSilent: Boolean
)
