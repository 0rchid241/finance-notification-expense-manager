package com.orchid241.financenotificationmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialTransactionDao {
    @Insert
    suspend fun insert(transaction: FinancialTransactionEntity): Long

    @Query("SELECT * FROM financial_transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<FinancialTransactionEntity>>
}
