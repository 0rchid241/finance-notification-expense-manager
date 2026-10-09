package com.orchid241.financenotificationmanager.data

import androidx.room.withTransaction
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.FinancialNotificationParserRegistry
import com.orchid241.financenotificationmanager.parser.ParseResult

object RawProcessingStatus {
    const val PROCESSED = "PROCESSED"
    const val PARSE_FAILED = "PARSE_FAILED"
}

/** Persists raw first, then atomically saves the parsed transaction and status. */
class FinancialTransactionRepository(
    private val database: AppDatabase,
    private val parserRegistry: FinancialNotificationParserRegistry = FinancialNotificationParserRegistry(),
) {
    private val rawRepository = RawNotificationRepository(database.rawNotificationDao())

    fun observeAll() = database.financialTransactionDao().observeAll()

    fun supportsPackage(packageName: String): Boolean =
        parserRegistry.supportsPackage(packageName)

    suspend fun collectNotification(
        notificationKey: String,
        packageName: String,
        title: String?,
        text: String?,
        postedAt: Long,
        receivedAt: Long,
    ): ParseResult? {
        if (!supportsPackage(packageName)) return null
        val id = rawRepository.saveRawNotification(notificationKey, packageName, title, text, postedAt, receivedAt)
        val raw = checkNotNull(database.rawNotificationDao().getById(id))
        val result = parserRegistry.parse(raw)
        database.withTransaction {
            when (result) {
                is ParseResult.Success -> {
                    val parsed = result.transaction
                    database.financialTransactionDao().insert(
                        FinancialTransactionEntity(
                            rawNotificationId = parsed.rawNotificationId,
                            transactionType = parsed.transactionType,
                            amount = parsed.amount,
                            counterparty = parsed.counterparty,
                            accountLast4 = parsed.accountLast4,
                            balance = parsed.balance,
                            source = parsed.source,
                            occurredAt = parsed.occurredAt,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    database.rawNotificationDao().updateProcessingStatus(id, RawProcessingStatus.PROCESSED)
                }
                ParseResult.Failure -> database.rawNotificationDao().updateProcessingStatus(id, RawProcessingStatus.PARSE_FAILED)
            }
        }
        return result
    }
}
