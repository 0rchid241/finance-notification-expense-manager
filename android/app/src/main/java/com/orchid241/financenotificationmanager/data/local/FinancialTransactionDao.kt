package com.orchid241.financenotificationmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialTransactionDao {
    @Query("SELECT * FROM financial_transactions WHERE id = :id")
    suspend fun getById(id: Long): FinancialTransactionEntity?

    @Update
    suspend fun update(transaction: FinancialTransactionEntity)

    @Query("DELETE FROM financial_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
    @Insert
    suspend fun insert(transaction: FinancialTransactionEntity): Long

    @Query("SELECT * FROM financial_transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transactions ORDER BY occurredAt DESC, id DESC")
    suspend fun getAll(): List<FinancialTransactionEntity>
}
