package com.orchid241.financenotificationmanager.data

import androidx.room.withTransaction
import com.orchid241.financenotificationmanager.consistency.TransactionConsistencyEngine
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.data.local.RuleMatchEntity
import com.orchid241.financenotificationmanager.data.local.UserRuleEntity
import com.orchid241.financenotificationmanager.parser.FinancialNotificationParserRegistry
import com.orchid241.financenotificationmanager.parser.ParseResult
import com.orchid241.financenotificationmanager.rules.RuleConditionType
import com.orchid241.financenotificationmanager.rules.TransactionRuleEngine
import com.orchid241.financenotificationmanager.rules.UserRule

object RawProcessingStatus {
    const val PROCESSED = "PROCESSED"
    const val PARSE_FAILED = "PARSE_FAILED"
}

/** Persists raw first, then atomically saves the parsed transaction, consistency candidates, rule matches, and status. */
class FinancialTransactionRepository(
    private val database: AppDatabase,
    private val parserRegistry: FinancialNotificationParserRegistry = FinancialNotificationParserRegistry(),
    private val consistencyEngine: TransactionConsistencyEngine = TransactionConsistencyEngine(),
    private val ruleEngine: TransactionRuleEngine = TransactionRuleEngine(),
) {
    private val rawRepository = RawNotificationRepository(database.rawNotificationDao())

    fun observeAll() = database.financialTransactionDao().observeAll()

    fun observeConsistencyCandidates() = database.consistencyCandidateDao().observeAll()

    fun observeUserRules() = database.userRuleDao().observeAll()

    fun observeRuleMatches() = database.ruleMatchDao().observeAll()

    fun supportsPackage(packageName: String): Boolean =
        parserRegistry.supportsPackage(packageName)

    suspend fun addAmountRule(
        name: String,
        amountThreshold: Long,
        message: String,
    ): Long {
        require(amountThreshold > 0)
        return addRule(
            UserRuleEntity(
                name = name.trim().ifEmpty { "큰 지출" },
                conditionType = RuleConditionType.EXPENSE_AMOUNT_AT_LEAST,
                amountThreshold = amountThreshold,
                message = message.trim().ifEmpty { "설정한 금액 이상의 지출이 발생했어요." },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun addCounterpartyRule(
        name: String,
        keyword: String,
        message: String,
    ): Long {
        val normalizedKeyword = keyword.trim()
        require(normalizedKeyword.isNotEmpty())
        return addRule(
            UserRuleEntity(
                name = name.trim().ifEmpty { "상대방 알림" },
                conditionType = RuleConditionType.COUNTERPARTY_CONTAINS,
                keyword = normalizedKeyword,
                message = message.trim().ifEmpty { "설정한 상대방과의 거래가 발생했어요." },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun addRule(rule: UserRuleEntity): Long = database.withTransaction {
        val ruleId = database.userRuleDao().insert(rule)
        val storedRule = rule.copy(id = ruleId).toRuleModel()
        val createdAt = System.currentTimeMillis()
        database.financialTransactionDao().getAll().forEach { transaction ->
            ruleEngine.evaluate(transaction, listOf(storedRule)).forEach { match ->
                database.ruleMatchDao().insert(
                    RuleMatchEntity(
                        ruleId = match.ruleId,
                        transactionId = match.transactionId,
                        ruleName = match.ruleName,
                        message = match.message,
                        createdAt = createdAt,
                    ),
                )
            }
        }
        ruleId
    }

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
                    val transaction = FinancialTransactionEntity(
                        rawNotificationId = parsed.rawNotificationId,
                        transactionType = parsed.transactionType,
                        amount = parsed.amount,
                        counterparty = parsed.counterparty,
                        accountLast4 = parsed.accountLast4,
                        balance = parsed.balance,
                        source = parsed.source,
                        occurredAt = parsed.occurredAt,
                        createdAt = System.currentTimeMillis(),
                    )
                    val transactionId = database.financialTransactionDao().insert(transaction)
                    val storedTransaction = transaction.copy(id = transactionId)

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

                    val rules = database.userRuleDao().getEnabled().map { it.toRuleModel() }
                    ruleEngine.evaluate(storedTransaction, rules).forEach { match ->
                        database.ruleMatchDao().insert(
                            RuleMatchEntity(
                                ruleId = match.ruleId,
                                transactionId = match.transactionId,
                                ruleName = match.ruleName,
                                message = match.message,
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

    private fun UserRuleEntity.toRuleModel(): UserRule = UserRule(
        id = id,
        name = name,
        enabled = enabled,
        conditionType = conditionType,
        amountThreshold = amountThreshold,
        keyword = keyword,
        message = message,
    )
}
