package com.orchid241.financenotificationmanager.data

import com.orchid241.financenotificationmanager.data.local.RawNotificationDao
import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity

class RawNotificationRepository(private val dao: RawNotificationDao) {
    suspend fun saveRawNotification(
        notificationKey: String,
        packageName: String,
        title: String?,
        text: String?,
        postedAt: Long,
        receivedAt: Long,
    ): Long = dao.insert(
        RawNotificationEntity(
            notificationKey = notificationKey,
            packageName = packageName,
            title = title,
            text = text,
            postedAt = postedAt,
            receivedAt = receivedAt,
        ),
    )

    suspend fun getAll(): List<RawNotificationEntity> = dao.getAll()

    suspend fun getByProcessingStatus(status: String): List<RawNotificationEntity> =
        dao.getByProcessingStatus(status)
}
