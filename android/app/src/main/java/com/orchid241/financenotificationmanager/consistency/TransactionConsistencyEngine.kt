package com.orchid241.financenotificationmanager.consistency

import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import kotlin.math.abs

enum class ConsistencyRelationType {
    DUPLICATE_CANDIDATE,
    INTERNAL_TRANSFER_CANDIDATE,
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
 * 이 엔진은 거래를 자동 삭제/병합하지 않는다. 오탐 가능성이 있으므로
 * 중복 또는 내부이체 가능성이 높은 쌍만 후보로 반환한다.
 */
class TransactionConsistencyEngine(
    private val duplicateWindowMillis: Long = 2 * 60 * 1000L,
    private val internalTransferWindowMillis: Long = 5 * 60 * 1000L,
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
        if (first.accountLast4 == second.accountLast4) return null
        if (!within(first.occurredAt, second.occurredAt, internalTransferWindowMillis)) return null

        return ConsistencyCandidate(
            firstTransactionId = first.id,
            secondTransactionId = second.id,
            relationType = ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE,
            reasons = listOf("출금/입금 한 쌍", "같은 금액", "서로 다른 계좌", "5분 이내"),
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
