package com.orchid241.financenotificationmanager.rules

import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRuleEngineTest {
    private val engine = TransactionRuleEngine()

    private fun transaction(
        id: Long = 1,
        type: TransactionType = TransactionType.WITHDRAWAL,
        amount: Long = 50_000,
        counterparty: String = "테스트 편의점",
    ) = FinancialTransactionEntity(
        id = id,
        rawNotificationId = id,
        transactionType = type,
        amount = amount,
        counterparty = counterparty,
        accountLast4 = "1234",
        balance = 100_000,
        source = "카카오뱅크",
        occurredAt = 1_000,
        createdAt = 2_000,
    )

    @Test
    fun largeExpenseRuleMatchesWithdrawalAtOrAboveThreshold() {
        val rule = UserRule(
            id = 10,
            name = "5만원 이상 지출",
            conditionType = RuleConditionType.EXPENSE_AMOUNT_AT_LEAST,
            amountThreshold = 50_000,
            message = "큰 지출이 발생했어요.",
        )

        val result = engine.evaluate(transaction(), listOf(rule))

        assertEquals(1, result.size)
        assertEquals(10L, result.single().ruleId)
        assertEquals(1L, result.single().transactionId)
    }

    @Test
    fun largeExpenseRuleDoesNotMatchDeposit() {
        val rule = UserRule(
            id = 10,
            name = "5만원 이상 지출",
            conditionType = RuleConditionType.EXPENSE_AMOUNT_AT_LEAST,
            amountThreshold = 50_000,
            message = "큰 지출이 발생했어요.",
        )

        assertTrue(engine.evaluate(transaction(type = TransactionType.DEPOSIT), listOf(rule)).isEmpty())
    }

    @Test
    fun counterpartyKeywordRuleIgnoresCaseAndWhitespace() {
        val rule = UserRule(
            id = 11,
            name = "편의점 사용",
            conditionType = RuleConditionType.COUNTERPARTY_CONTAINS,
            keyword = "테스트편의점",
            message = "편의점 지출을 확인해 주세요.",
        )

        val result = engine.evaluate(transaction(counterparty = "테스트 편의점"), listOf(rule))

        assertEquals(1, result.size)
        assertEquals("편의점 지출을 확인해 주세요.", result.single().message)
    }

    @Test
    fun disabledAndInvalidRulesAreIgnored() {
        val rules = listOf(
            UserRule(
                id = 1,
                name = "꺼진 규칙",
                enabled = false,
                conditionType = RuleConditionType.EXPENSE_AMOUNT_AT_LEAST,
                amountThreshold = 1,
                message = "표시되면 안 됨",
            ),
            UserRule(
                id = 2,
                name = "잘못된 금액",
                conditionType = RuleConditionType.EXPENSE_AMOUNT_AT_LEAST,
                amountThreshold = 0,
                message = "표시되면 안 됨",
            ),
            UserRule(
                id = 3,
                name = "빈 키워드",
                conditionType = RuleConditionType.COUNTERPARTY_CONTAINS,
                keyword = "   ",
                message = "표시되면 안 됨",
            ),
        )

        assertTrue(engine.evaluate(transaction(), rules).isEmpty())
    }

    @Test
    fun oneTransactionCanMatchMultipleRules() {
        val rules = listOf(
            UserRule(
                id = 1,
                name = "3만원 이상 지출",
                conditionType = RuleConditionType.EXPENSE_AMOUNT_AT_LEAST,
                amountThreshold = 30_000,
                message = "금액 규칙",
            ),
            UserRule(
                id = 2,
                name = "편의점 사용",
                conditionType = RuleConditionType.COUNTERPARTY_CONTAINS,
                keyword = "편의점",
                message = "상대방 규칙",
            ),
        )

        val result = engine.evaluate(transaction(), rules)

        assertEquals(listOf(1L, 2L), result.map { it.ruleId })
    }
}
