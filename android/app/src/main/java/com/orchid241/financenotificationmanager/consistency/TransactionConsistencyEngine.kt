package com.orchid241.financenotificationmanager.consistency

import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import kotlin.math.abs

enum class ConsistencyRelationType {
    DUPLICATE_CANDIDATE,
    INTERNAL_TRANSFER_CANDIDATE,
    REFUND_CANDIDATE,
}

data class ConsistencyCandidate(
    val firstTransactionId: Long,
    val secondTransactionId: Long,
    val relationType: ConsistencyRelationType,
    val reasons: List<String>,
)

/**
 * 구조화된 거래끼리 비교해 정합성 검토가 필요한 후보를 찾는다.
 *
 * 이 엔진은 거래를 자동 삭제/병합하거나 취소·환불을 확정하지 않는다.
 * 오탐 가능성이 있으므로 중복, 내부이체, 취소·환불 가능성이 높은 쌍만 후보로 반환한다.
 */
class TransactionConsistencyEngine(
    private val duplicateWindowMillis: Long = 2 * 60 * 1000L,
    private val internalTransferWindowMillis: Long = 5 * 60 * 1000L,
    private val refundWindowMillis: Long = 14 * 24 * 60 * 60 * 1000L,
) {
    fun findCandidates(transactions: List<FinancialTransactionEntity>): List<ConsistencyCandidate> {
        if (transactions.size < 2) return emptyList()

        val result = mutableListOf<ConsistencyCandidate>()
        for (i in 0 until transactions.lastIndex) {
            for (j in i + 1 until transactions.size) {
                val first = transactions[i]
                val second = transactions[j]

                duplicateCandidate(first, second)?.let(result::add)
                    ?: internalTransferCandidate(first, second)?.let(result::add)
                    ?: refundCandidate(first, second)?.let(result::add)
            }
        }
        return result
    }

    private fun duplicateCandidate(
        first: FinancialTransactionEntity,
        second: FinancialTransactionEntity,
    ): ConsistencyCandidate? {
        if (first.transactionType != second.transactionType) return null
        if (first.amount != second.amount) return null
        if (first.source != second.source) return null
        if (first.accountLast4 != second.accountLast4) return null
        if (normalize(first.counterparty) != normalize(second.counterparty)) return null
        if (!within(first.occurredAt, second.occurredAt, duplicateWindowMillis)) return null

        return ConsistencyCandidate(
            firstTransactionId = first.id,
            secondTransactionId = second.id,
            relationType = ConsistencyRelationType.DUPLICATE_CANDIDATE,
            reasons = listOf("같은 거래 유형", "같은 금액", "같은 출처/계좌", "같은 상대방", "2분 이내"),
        )
    }

    private fun internalTransferCandidate(
        first: FinancialTransactionEntity,
        second: FinancialTransactionEntity,
    ): ConsistencyCandidate? {
        if (!isOppositeDirection(first.transactionType, second.transactionType)) return null
        if (first.amount != second.amount) return null
        if (first.accountLast4.isBlank() || second.accountLast4.isBlank()) return null
        if (first.accountLast4 == second.accountLast4) return null
        if (!within(first.occurredAt, second.occurredAt, internalTransferWindowMillis)) return null

        return ConsistencyCandidate(
            firstTransactionId = first.id,
            secondTransactionId = second.id,
            relationType = ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE,
            reasons = listOf("출금/입금 한 쌍", "같은 금액", "서로 다른 계좌", "5분 이내"),
        )
    }

    private fun refundCandidate(
        first: FinancialTransactionEntity,
        second: FinancialTransactionEntity,
    ): ConsistencyCandidate? {
        val withdrawal = when {
            first.transactionType == TransactionType.WITHDRAWAL && second.transactionType == TransactionType.DEPOSIT -> first
            second.transactionType == TransactionType.WITHDRAWAL && first.transactionType == TransactionType.DEPOSIT -> second
            else -> return null
        }
        val deposit = if (withdrawal.id == first.id) second else first

        if (withdrawal.amount != deposit.amount) return null
        if (withdrawal.accountLast4.isBlank() || deposit.accountLast4.isBlank()) return null
        if (withdrawal.accountLast4 != deposit.accountLast4) return null

        val withdrawalCounterparty = normalize(withdrawal.counterparty)
        val depositCounterparty = normalize(deposit.counterparty)
        if (withdrawalCounterparty.isBlank() || depositCounterparty.isBlank()) return null
        if (withdrawalCounterparty != depositCounterparty) return null

        if (deposit.occurredAt <= withdrawal.occurredAt) return null
        if (deposit.occurredAt - withdrawal.occurredAt > refundWindowMillis) return null

        return ConsistencyCandidate(
            firstTransactionId = withdrawal.id,
            secondTransactionId = deposit.id,
            relationType = ConsistencyRelationType.REFUND_CANDIDATE,
            reasons = listOf("출금 후 입금", "같은 금액", "같은 계좌", "같은 상대방", "14일 이내"),
        )
    }

    private fun isOppositeDirection(first: TransactionType, second: TransactionType): Boolean =
        (first == TransactionType.WITHDRAWAL && second == TransactionType.DEPOSIT) ||
            (first == TransactionType.DEPOSIT && second == TransactionType.WITHDRAWAL)

    private fun within(first: Long, second: Long, windowMillis: Long): Boolean =
        abs(first - second) <= windowMillis

    private fun normalize(value: String): String =
        value.filterNot(Char::isWhitespace).lowercase()
}
