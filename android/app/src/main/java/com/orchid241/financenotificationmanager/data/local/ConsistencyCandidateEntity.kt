package com.orchid241.financenotificationmanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType

@Entity(
    tableName = "consistency_candidates",
    foreignKeys = [
        ForeignKey(
            entity = FinancialTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["firstTransactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FinancialTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["secondTransactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("firstTransactionId"),
        Index("secondTransactionId"),
        Index(
            value = ["firstTransactionId", "secondTransactionId", "relationType"],
            unique = true,
        ),
    ],
)
data class ConsistencyCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstTransactionId: Long,
    val secondTransactionId: Long,
    val relationType: ConsistencyRelationType,
    val reasonText: String,
    val createdAt: Long,
)

class ConsistencyRelationTypeConverters {
    @TypeConverter
    fun encode(value: ConsistencyRelationType): String = value.name

    @TypeConverter
    fun decode(value: String): ConsistencyRelationType = ConsistencyRelationType.valueOf(value)
}
