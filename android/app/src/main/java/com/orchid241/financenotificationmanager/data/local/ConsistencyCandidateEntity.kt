package com.orchid241.financenotificationmanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "'PENDING'") val reviewStatus: CandidateReviewStatus = CandidateReviewStatus.PENDING,
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(defaultValue = "NULL") val reviewedAt: Long? = null,
)

enum class CandidateReviewStatus { PENDING, CONFIRMED, REJECTED, NEEDS_REVIEW }

class ConsistencyRelationTypeConverters {
    @TypeConverter fun encodeReview(value: CandidateReviewStatus): String = value.name
    @TypeConverter fun decodeReview(value: String): CandidateReviewStatus = CandidateReviewStatus.valueOf(value)
    @TypeConverter
    fun encode(value: ConsistencyRelationType): String = value.name

    @TypeConverter
    fun decode(value: String): ConsistencyRelationType = ConsistencyRelationType.valueOf(value)
}
