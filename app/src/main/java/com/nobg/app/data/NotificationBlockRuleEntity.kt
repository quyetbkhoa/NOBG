package com.nobg.app.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "notification_block_rules",
    primaryKeys = ["packageName", "userId", "keyword"],
    indices = [Index(value = ["createdAt"])]
)
data class NotificationBlockRuleEntity(
    val packageName: String,
    val userId: Int,
    val keyword: String,
    val appLabel: String,
    val createdAt: Long
)
