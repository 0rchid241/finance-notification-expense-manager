package com.orchid241.financenotificationmanager.data

import androidx.room.withTransaction
import com.orchid241.financenotificationmanager.consistency.TransactionConsistencyEngine
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.FinancialNotificationParserRegistry
import com.orchid241.financenotificationmanager.parser.ParseResult

object RawProcessingStatus {
    const val PROCESSED = "PROCESSED"
    const val PARSE_FAILED = "PARSE_FAILED"
}

/** Persists raw first, then atomically saves the parsed transaction, consistency candidates, and status. */
class FinancialTransactionRepository(
    private val database: AppDatabase,
    private val parserRegistry: FinancialNotificationParserRegistry = FinancialNotificationParserRegistry(),
    private val consistencyEngine: TransactionConsistencyEngine = TransactionConsistencyEngine(),
) {
    private val rawRepository = RawNotificationRepository(database.rawNotificationDao())

    fun observeAll() = database.financialTransactionDao().observeAll()

    fun observeConsistencyCandidates() = database.consistencyCandidateDao().observeAll()

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
                    val transactionId = database.financialTransactionDao().insert(
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

                    val candidates = consistencyEngine
                        .findCandidates(database.financialTransactionDao().getAll())
                        .filter { candidate ->
                            candidate.firstTransactionId == transactionId || candidate.secondTransactionId == transactionId
                        }

                    val createdAt = System.currentTimeMillis()
                    candidates.forEach { candidate ->
                        database.consistencyCandidateDao().insert(
                            ConsistencyCandidateEntity(
                                firstTransactionId = candidate.firstTransactionId,
                                secondTransactionId = candidate.secondTransactionId,
                                relationType = candidate.relationType,
                                reasonText = candidate.reasons.joinToString(" · "),
                                createdAt = createdAt,
                            ),
                        )
                    }
                    database.rawNotificationDao().updateProcessingStatus(id, RawProcessingStatus.PROCESSED)
                }
                ParseResult.Failure -> database.rawNotificationDao().updateProcessingStatus(id, RawProcessingStatus.PARSE_FAILED)
            }
        }
        return result
    }
}
