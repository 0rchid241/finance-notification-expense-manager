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
import com.orchid241.financenotificationmanager.parser.TransactionType
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

    suspend fun addManualTransaction(input: ManualTransactionInput): Long {
        val valid = input.validated()
        return database.withTransaction {
            saveTransactionAndEvaluate(
                FinancialTransactionEntity(
                    rawNotificationId = null,
                    transactionType = valid.transactionType,
                    amount = valid.amount,
                    counterparty = valid.counterparty,
                    accountLast4 = valid.accountLast4,
                    balance = valid.balance,
                    source = "수동 입력",
                    occurredAt = valid.occurredAt,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    // 알림 거래와 수동 거래가 같은 정합성·규칙 평가 경로를 사용한다. 호출자는 Room 트랜잭션 안에 있어야 한다.
    private suspend fun saveTransactionAndEvaluate(transaction: FinancialTransactionEntity): Long {
        val transactionId = database.financialTransactionDao().insert(transaction)
        val storedTransaction = transaction.copy(id = transactionId)
        val createdAt = System.currentTimeMillis()
        consistencyEngine.findCandidates(database.financialTransactionDao().getAll())
            .filter { it.firstTransactionId == transactionId || it.secondTransactionId == transactionId }
            .forEach { candidate ->
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
        return transactionId
    }

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

    suspend fun setRuleEnabled(ruleId: Long, enabled: Boolean) {
        database.userRuleDao().setEnabled(ruleId, enabled)
    }

    suspend fun deleteRule(ruleId: Long) {
        database.userRuleDao().deleteById(ruleId)
    }

    suspend fun clearAllData() {
        database.clearAllTables()
    }

    /**
     * Debug 빌드에서 발표/개발 검증에 사용할 취소·환불 시연 거래 2건을 생성한다.
     * 실제 알림 수집 흐름과 분리되어 있으며, 같은 계좌/상대방/금액의 출금 후 입금으로 구성한다.
     */
    suspend fun addRefundDemoData() = database.withTransaction {
        val now = System.currentTimeMillis()
        val withdrawalAt = now - 2 * 60 * 60 * 1000L
        val refundAt = now - 60 * 60 * 1000L
        val demoKey = "demo-refund-$now"

        val withdrawalRawId = rawRepository.saveRawNotification(
            notificationKey = "$demoKey-withdrawal",
            packageName = "demo.presentation",
            title = "[시연] 카카오뱅크 출금",
            text = "스타벅스 정릉점 8,000원 출금",
            postedAt = withdrawalAt,
            receivedAt = withdrawalAt,
        )
        val refundRawId = rawRepository.saveRawNotification(
            notificationKey = "$demoKey-refund",
            packageName = "demo.presentation",
            title = "[시연] 카카오뱅크 입금",
            text = "스타벅스 정릉점 8,000원 입금",
            postedAt = refundAt,
            receivedAt = refundAt,
        )

        val withdrawalId = database.financialTransactionDao().insert(
            FinancialTransactionEntity(
                rawNotificationId = withdrawalRawId,
                transactionType = TransactionType.WITHDRAWAL,
                amount = 8_000,
                counterparty = "스타벅스 정릉점",
                accountLast4 = "2410",
                balance = 492_000,
                source = "카카오뱅크 · 시연",
                occurredAt = withdrawalAt,
                createdAt = now,
            ),
        )
        val refundId = database.financialTransactionDao().insert(
            FinancialTransactionEntity(
                rawNotificationId = refundRawId,
                transactionType = TransactionType.DEPOSIT,
                amount = 8_000,
                counterparty = "스타벅스 정릉점",
                accountLast4 = "2410",
                balance = 500_000,
                source = "카카오뱅크 · 시연",
                occurredAt = refundAt,
                createdAt = now,
            ),
        )

        val demoIds = setOf(withdrawalId, refundId)
        val createdAt = System.currentTimeMillis()
        consistencyEngine
            .findCandidates(database.financialTransactionDao().getAll())
            .filter { candidate ->
                candidate.firstTransactionId in demoIds || candidate.secondTransactionId in demoIds
            }
            .forEach { candidate ->
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
        database.financialTransactionDao().getAll()
            .filter { it.id in demoIds }
            .forEach { transaction ->
                ruleEngine.evaluate(transaction, rules).forEach { match ->
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

        database.rawNotificationDao().updateProcessingStatus(withdrawalRawId, RawProcessingStatus.PROCESSED)
        database.rawNotificationDao().updateProcessingStatus(refundRawId, RawProcessingStatus.PROCESSED)
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
                    saveTransactionAndEvaluate(transaction)
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
