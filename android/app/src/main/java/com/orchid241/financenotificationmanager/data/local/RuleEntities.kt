package com.orchid241.financenotificationmanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.orchid241.financenotificationmanager.rules.RuleConditionType

@Entity(tableName = "user_rules")
data class UserRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val conditionType: RuleConditionType,
    val amountThreshold: Long? = null,
    val keyword: String? = null,
    val message: String,
    val createdAt: Long,
)

@Entity(
    tableName = "rule_matches",
    foreignKeys = [
        ForeignKey(
            entity = UserRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FinancialTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("ruleId"),
        Index("transactionId"),
        Index(value = ["ruleId", "transactionId"], unique = true),
    ],
)
data class RuleMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val transactionId: Long,
    val ruleName: String,
    val message: String,
    val createdAt: Long,
)

class RuleConditionTypeConverters {
    @TypeConverter
    fun encode(value: RuleConditionType): String = value.name

    @TypeConverter
    fun decode(value: String): RuleConditionType = RuleConditionType.valueOf(value)
}
