package com.orchid241.financenotificationmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RawNotificationDao {
    @Query("SELECT * FROM raw_notifications WHERE id = :id")
    suspend fun getById(id: Long): RawNotificationEntity?

    @Query("UPDATE raw_notifications SET processingStatus = :status WHERE id = :id")
    suspend fun updateProcessingStatus(id: Long, status: String)

    @Insert
    suspend fun insert(notification: RawNotificationEntity): Long

    @Query("SELECT * FROM raw_notifications ORDER BY id ASC")
    suspend fun getAll(): List<RawNotificationEntity>

    @Query("SELECT * FROM raw_notifications WHERE processingStatus = :status ORDER BY id ASC")
    suspend fun getByProcessingStatus(status: String): List<RawNotificationEntity>
}
