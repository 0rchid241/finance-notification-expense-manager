package com.orchid241.financenotificationmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RawNotificationDao {
    @Insert
    suspend fun insert(notification: RawNotificationEntity): Long

    @Query("SELECT * FROM raw_notifications ORDER BY id ASC")
    suspend fun getAll(): List<RawNotificationEntity>

    @Query("SELECT * FROM raw_notifications WHERE processingStatus = :status ORDER BY id ASC")
    suspend fun getByProcessingStatus(status: String): List<RawNotificationEntity>
}
