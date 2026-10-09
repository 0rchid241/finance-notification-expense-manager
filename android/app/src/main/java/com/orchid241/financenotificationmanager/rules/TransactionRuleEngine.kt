package com.orchid241.financenotificationmanager.rules

import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType

enum class RuleConditionType {
    EXPENSE_AMOUNT_AT_LEAST,
    COUNTERPARTY_CONTAINS,
}

data class UserRule(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    val conditionType: RuleConditionType,
    val amountThreshold: Long? = null,
    val keyword: String? = null,
    val message: String,
)

data class RuleMatch(
    val ruleId: Long,
    val ruleName: String,
    val transactionId: Long,
    val message: String,
)

/**
 * 정규화된 거래에 사용자가 정의한 규칙을 적용한다.
 *
 * 잘못 구성된 규칙은 거래를 잘못 경고하지 않도록 매치하지 않는다.
 */
class TransactionRuleEngine {
    fun evaluate(
        transaction: FinancialTransactionEntity,
        rules: List<UserRule>,
    ): List<RuleMatch> = rules
        .asSequence()
        .filter(UserRule::enabled)
        .filter { matches(transaction, it) }
        .map { rule ->
            RuleMatch(
                ruleId = rule.id,
                ruleName = rule.name,
                transactionId = transaction.id,
                message = rule.message,
            )
        }
        .toList()

    private fun matches(transaction: FinancialTransactionEntity, rule: UserRule): Boolean =
        when (rule.conditionType) {
            RuleConditionType.EXPENSE_AMOUNT_AT_LEAST -> {
                val threshold = rule.amountThreshold?.takeIf { it > 0 } ?: return false
                transaction.transactionType == TransactionType.WITHDRAWAL &&
                    transaction.amount >= threshold
            }

            RuleConditionType.COUNTERPARTY_CONTAINS -> {
                val keyword = rule.keyword?.trim()?.takeIf(String::isNotEmpty) ?: return false
                normalize(transaction.counterparty).contains(normalize(keyword))
            }
        }

    private fun normalize(value: String): String = value
        .filterNot(Char::isWhitespace)
        .lowercase()
}
