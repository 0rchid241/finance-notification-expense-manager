package com.orchid241.financenotificationmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsistencyCandidateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(candidate: ConsistencyCandidateEntity): Long

    @Query("SELECT * FROM consistency_candidates ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ConsistencyCandidateEntity>>

    @Query("SELECT * FROM consistency_candidates ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<ConsistencyCandidateEntity>
}
