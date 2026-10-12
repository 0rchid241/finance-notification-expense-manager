package com.orchid241.financenotificationmanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.orchid241.financenotificationmanager.parser.TransactionType

@Entity(
    tableName = "financial_transactions",
    foreignKeys = [ForeignKey(
        entity = RawNotificationEntity::class,
        parentColumns = ["id"], childColumns = ["rawNotificationId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("rawNotificationId")],
)
data class FinancialTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // 수동 거래는 원본 알림을 만들지 않고 null로 저장한다.
    val rawNotificationId: Long?,
    val transactionType: TransactionType,
    val amount: Long,
    val counterparty: String,
    val accountLast4: String,
    val balance: Long?,
    val source: String,
    val occurredAt: Long,
    val createdAt: Long,
)

class TransactionTypeConverters {
    @TypeConverter fun encode(value: TransactionType): String = value.name
    @TypeConverter fun decode(value: String): TransactionType = TransactionType.valueOf(value)
}
