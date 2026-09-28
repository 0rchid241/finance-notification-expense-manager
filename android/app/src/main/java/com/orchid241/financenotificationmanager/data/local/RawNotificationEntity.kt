package com.orchid241.financenotificationmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Each callback is a separate raw event, even when notificationKey is repeated.
@Entity(tableName = "raw_notifications")
data class RawNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val receivedAt: Long,
    val processingStatus: String = "PENDING",
)
