package com.orchid241.financenotificationmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsistencyCandidateDao {
    @Update
    suspend fun update(candidate: ConsistencyCandidateEntity)

    @Query("SELECT * FROM consistency_candidates WHERE id = :id")
    suspend fun getById(id: Long): ConsistencyCandidateEntity?

    @Query("SELECT * FROM consistency_candidates WHERE relationType = :type AND ((firstTransactionId = :first AND secondTransactionId = :second) OR (firstTransactionId = :second AND secondTransactionId = :first)) LIMIT 1")
    suspend fun getPair(first: Long, second: Long, type: ConsistencyRelationType): ConsistencyCandidateEntity?

    @Query("UPDATE consistency_candidates SET isActive = 0 WHERE firstTransactionId = :id OR secondTransactionId = :id")
    suspend fun deactivateForTransaction(id: Long)

    @Query("UPDATE consistency_candidates SET reviewStatus = 'NEEDS_REVIEW' WHERE reviewStatus = 'CONFIRMED' AND (firstTransactionId = :id OR secondTransactionId = :id)")
    suspend fun invalidateConfirmedForTransaction(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(candidate: ConsistencyCandidateEntity): Long

    @Query("SELECT * FROM consistency_candidates ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ConsistencyCandidateEntity>>

    @Query("SELECT * FROM consistency_candidates ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<ConsistencyCandidateEntity>
}
