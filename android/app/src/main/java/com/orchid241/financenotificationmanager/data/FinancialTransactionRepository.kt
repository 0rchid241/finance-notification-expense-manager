package com.orchid241.financenotificationmanager.data

import androidx.room.withTransaction
import com.orchid241.financenotificationmanager.consistency.TransactionConsistencyEngine
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import com.orchid241.financenotificationmanager.data.local.CandidateReviewStatus
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.data.local.RuleMatchEntity
import com.orchid241.financenotificationmanager.data.local.UserRuleEntity
import com.orchid241.financenotificationmanager.parser.FinancialNotificationParserRegistry
import com.orchid241.financenotificationmanager.parser.ParseResult
import com.orchid241.financenotificationmanager.parser.SupportedFinancialApps
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.rules.RuleConditionType
import com.orchid241.financenotificationmanager.rules.TransactionRuleEngine
import com.orchid241.financenotificationmanager.rules.UserRule

object RawProcessingStatus {
    const val PROCESSED = "PROCESSED"
    const val PARSE_FAILED = "PARSE_FAILED"
    const val IGNORED = "IGNORED"
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

    // Raw collection is based on known financial app packages, not parser readiness.
    // Unsupported notification formats remain available as PARSE_FAILED raw records.
    fun supportsPackage(packageName: String): Boolean =
        SupportedFinancialApps.supports(packageName)

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

    suspend fun updateTransaction(id: Long, input: ManualTransactionInput) {
        val valid = input.validated()
        database.withTransaction {
            val old = requireNotNull(database.financialTransactionDao().getById(id)) { "거래가 존재하지 않습니다." }
            val updated = old.copy(
                occurredAt = valid.occurredAt, transactionType = valid.transactionType,
                amount = valid.amount, counterparty = valid.counterparty,
                accountLast4 = valid.accountLast4, balance = valid.balance,
            )
            database.financialTransactionDao().update(updated)
            // 잔액만 바뀌면 관계 판별 값은 같으므로 기존 확정을 유지한다.
            if (old.copy(balance = updated.balance) != updated) {
                database.consistencyCandidateDao().invalidateConfirmedForTransaction(id)
            }
            recalculateCandidates(id)
            database.ruleMatchDao().deleteForTransaction(id)
            evaluateRules(updated)
        }
    }

    suspend fun deleteTransaction(id: Long) = database.withTransaction {
        // 연결 후보/규칙 결과는 CASCADE로 정리한다. RawNotification은 그대로 보존한다.
        database.financialTransactionDao().deleteById(id)
    }

    suspend fun reviewCandidate(id: Long, confirmed: Boolean) = database.withTransaction {
        val dao = database.consistencyCandidateDao()
        val candidate = requireNotNull(dao.getById(id)) { "후보가 존재하지 않습니다." }
        if (confirmed) {
            require(candidate.isActive) { "수정 후 조건이 달라진 관계는 확정할 수 없습니다." }
            val involved = setOf(candidate.firstTransactionId, candidate.secondTransactionId)
            require(dao.getAll().none {
                it.id != id && it.isActive && it.reviewStatus == CandidateReviewStatus.CONFIRMED &&
                    (it.firstTransactionId in involved || it.secondTransactionId in involved)
            }) { "이미 확정된 관계가 있습니다. 기존 확정을 후보 아님으로 변경한 뒤 다시 확인해 주세요." }
        }
        dao.update(candidate.copy(
            reviewStatus = if (confirmed) CandidateReviewStatus.CONFIRMED else CandidateReviewStatus.REJECTED,
            reviewedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun recalculateConsistency() = database.withTransaction { recalculateCandidates(null) }

    private suspend fun recalculateCandidates(transactionId: Long?) {
        val dao = database.consistencyCandidateDao()
        if (transactionId != null) dao.deactivateForTransaction(transactionId)
        else database.financialTransactionDao().getAll().forEach { dao.deactivateForTransaction(it.id) }
        consistencyEngine.findCandidates(database.financialTransactionDao().getAll())
            .filter { transactionId == null || it.firstTransactionId == transactionId || it.secondTransactionId == transactionId }
            .forEach { detected ->
                val existing = dao.getPair(detected.firstTransactionId, detected.secondTransactionId, detected.relationType)
                val candidate = ConsistencyCandidateEntity(
                    firstTransactionId = detected.firstTransactionId,
                    secondTransactionId = detected.secondTransactionId,
                    relationType = detected.relationType,
                    reasonText = detected.reasons.joinToString(" · "),
                    createdAt = System.currentTimeMillis(),
                )
                if (existing == null) dao.insert(candidate)
                else dao.update(existing.copy(
                    firstTransactionId = detected.firstTransactionId, secondTransactionId = detected.secondTransactionId,
                    reasonText = candidate.reasonText, isActive = true,
                )) // 사용자 거절/확정은 자동 탐지로 덮어쓰지 않는다.
            }
    }

    private suspend fun evaluateRules(transaction: FinancialTransactionEntity) {
        val rules = database.userRuleDao().getEnabled().map { it.toRuleModel() }
        ruleEngine.evaluate(transaction, rules).forEach { match ->
            database.ruleMatchDao().insert(RuleMatchEntity(
                ruleId = match.ruleId, transactionId = match.transactionId,
                ruleName = match.ruleName, message = match.message, createdAt = System.currentTimeMillis(),
            ))
        }
    }

    // 알림 거래와 수동 거래가 같은 정합성·규칙 평가 경로를 사용한다. 호출자는 Room 트랜잭션 안에 있어야 한다.
    private suspend fun saveTransactionAndEvaluate(transaction: FinancialTransactionEntity): Long {
        val transactionId = database.financialTransactionDao().insert(transaction)
        val storedTransaction = transaction.copy(id = transactionId)
        recalculateCandidates(transactionId)
        evaluateRules(storedTransaction)
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
        database.withTransaction {
            database.userRuleDao().setEnabled(ruleId, enabled)
            if (enabled) database.financialTransactionDao().getAll().forEach { evaluateRules(it) }
        }
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
                ParseResult.Ignored -> database.rawNotificationDao().updateProcessingStatus(id, RawProcessingStatus.IGNORED)
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
