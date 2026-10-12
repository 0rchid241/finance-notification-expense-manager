package com.orchid241.financenotificationmanager.data

import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.local.CandidateReviewStatus
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType

data class MonthlyTotals(
    val totalWithdrawal: Long,
    val totalDeposit: Long,
    val confirmedRefund: Long,
    val duplicateExcludedWithdrawal: Long,
    val duplicateExcludedDeposit: Long,
) {
    val duplicateExcluded: Long get() = duplicateExcludedWithdrawal + duplicateExcludedDeposit
    val netExpense: Long get() = totalWithdrawal - duplicateExcludedWithdrawal - confirmedRefund
    val reflectedDeposit: Long get() = totalDeposit - duplicateExcludedDeposit
}

/** 총액은 원본 거래 전체 기준이다. 후보만으로는 보정하지 않고, 환불은 입금 발생 월에 반영한다. */
fun monthlyTotals(
    transactions: List<FinancialTransactionEntity>,
    candidates: List<ConsistencyCandidateEntity>,
    range: LongRange,
): MonthlyTotals {
    val existingIds = transactions.map { it.id }.toSet()
    val confirmed = candidates.filter {
        it.isActive && it.reviewStatus == CandidateReviewStatus.CONFIRMED &&
            it.firstTransactionId in existingIds && it.secondTransactionId in existingIds
    }
    // 중복 확정 쌍에서는 이전 ID를 남기고 새 ID의 거래를 집계에서 제외한다.
    val excluded = confirmed.filter { it.relationType == ConsistencyRelationType.DUPLICATE_CANDIDATE }
        .map { maxOf(it.firstTransactionId, it.secondTransactionId) }.toSet()
    val month = transactions.filter { it.occurredAt in range }
    val refunds = confirmed.filter { it.relationType == ConsistencyRelationType.REFUND_CANDIDATE }
        .map { it.secondTransactionId }.toSet()
    return MonthlyTotals(
        totalWithdrawal = month.filter { it.transactionType == TransactionType.WITHDRAWAL }.sumOf { it.amount },
        totalDeposit = month.filter { it.transactionType == TransactionType.DEPOSIT }.sumOf { it.amount },
        confirmedRefund = month.filter { it.id in refunds && it.id !in excluded && it.transactionType == TransactionType.DEPOSIT }.sumOf { it.amount },
        duplicateExcludedWithdrawal = month.filter { it.id in excluded && it.transactionType == TransactionType.WITHDRAWAL }.sumOf { it.amount },
        duplicateExcludedDeposit = month.filter { it.id in excluded && it.transactionType == TransactionType.DEPOSIT }.sumOf { it.amount },
    )
}
