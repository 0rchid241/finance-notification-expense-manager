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
}

@Dao
interface RuleMatchDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(match: RuleMatchEntity): Long

    @Query("SELECT * FROM rule_matches ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<RuleMatchEntity>>
}
