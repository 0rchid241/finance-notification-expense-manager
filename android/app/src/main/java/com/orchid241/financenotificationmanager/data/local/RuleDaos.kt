package com.orchid241.financenotificationmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserRuleDao {
    @Insert
    suspend fun insert(rule: UserRuleEntity): Long

    @Query("SELECT * FROM user_rules ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<UserRuleEntity>>

    @Query("SELECT * FROM user_rules WHERE enabled = 1 ORDER BY createdAt ASC, id ASC")
    suspend fun getEnabled(): List<UserRuleEntity>

    @Query("UPDATE user_rules SET enabled = :enabled WHERE id = :ruleId")
    suspend fun setEnabled(ruleId: Long, enabled: Boolean)

    @Query("DELETE FROM user_rules WHERE id = :ruleId")
    suspend fun deleteById(ruleId: Long)
}

@Dao
interface RuleMatchDao {
    @Query("DELETE FROM rule_matches WHERE transactionId = :id")
    suspend fun deleteForTransaction(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(match: RuleMatchEntity): Long

    @Query("SELECT * FROM rule_matches ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<RuleMatchEntity>>
}
