package com.orchid241.financenotificationmanager.data

import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.local.CandidateReviewStatus
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import org.junit.Assert.*
import org.junit.Test

class MonthlyTotalsTest {
    private fun transaction(id: Long, type: TransactionType = TransactionType.WITHDRAWAL, time: Long = 1000) =
        FinancialTransactionEntity(id, null, type, 5000, "가상매장", "0001", null, "수동 입력", time, time)
    private fun relation(type: ConsistencyRelationType, status: CandidateReviewStatus = CandidateReviewStatus.CONFIRMED) =
        ConsistencyCandidateEntity(1, 1, 2, type, "검증 사유", 1000, status)

    @Test fun normalDepositsAndUnconfirmedCandidatesDoNotReduceExpense() {
        val totals = monthlyTotals(listOf(transaction(1), transaction(2, TransactionType.DEPOSIT)), listOf(relation(ConsistencyRelationType.REFUND_CANDIDATE, CandidateReviewStatus.PENDING)), 1000L..1999)
        assertEquals(5000L, totals.totalWithdrawal)
        assertEquals(5000L, totals.totalDeposit)
        assertEquals(5000L, totals.netExpense)
        assertEquals(0L, totals.confirmedRefund)
    }
    @Test fun confirmedDuplicateDepositReducesReflectedIncomeNotNetExpense() {
        val totals = monthlyTotals(listOf(transaction(1, TransactionType.DEPOSIT), transaction(2, TransactionType.DEPOSIT)), listOf(relation(ConsistencyRelationType.DUPLICATE_CANDIDATE)), 1000L..1999)
        assertEquals(10000L, totals.totalDeposit)
        assertEquals(5000L, totals.duplicateExcludedDeposit)
        assertEquals(5000L, totals.reflectedDeposit)
        assertEquals(0L, totals.netExpense)
    }
    @Test fun refundIsCreditedInDepositMonthAndCanProduceNegativeNetExpense() {
        val transactions = listOf(transaction(1, time = 900), transaction(2, TransactionType.DEPOSIT, 1100))
        val candidates = listOf(relation(ConsistencyRelationType.REFUND_CANDIDATE))
        assertEquals(5000L, monthlyTotals(transactions, candidates, 0L..999).netExpense)
        assertEquals(-5000L, monthlyTotals(transactions, candidates, 1000L..1999).netExpense)
    }
    @Test fun duplicateExclusionFollowsExcludedTransactionsMonth() {
        val transactions = listOf(transaction(1, time = 900), transaction(2, time = 1100))
        val candidates = listOf(relation(ConsistencyRelationType.DUPLICATE_CANDIDATE))
        assertEquals(5000L, monthlyTotals(transactions, candidates, 0L..999).netExpense)
        assertEquals(0L, monthlyTotals(transactions, candidates, 1000L..1999).netExpense)
    }
    @Test fun rejectedInactiveAndNeedsReviewDoNotAdjustTotals() {
        val transactions = listOf(transaction(1), transaction(2))
        for (status in listOf(CandidateReviewStatus.REJECTED, CandidateReviewStatus.NEEDS_REVIEW, CandidateReviewStatus.PENDING)) {
            assertEquals(10000L, monthlyTotals(transactions, listOf(relation(ConsistencyRelationType.DUPLICATE_CANDIDATE, status)), 1000L..1999).netExpense)
        }
        assertEquals(10000L, monthlyTotals(transactions, listOf(relation(ConsistencyRelationType.DUPLICATE_CANDIDATE).copy(isActive = false)), 1000L..1999).netExpense)
    }
    @Test fun internalTransferConfirmationOnlyRecordsReviewAndMissingTransactionsAreIgnored() {
        assertEquals(5000L, monthlyTotals(listOf(transaction(1), transaction(2, TransactionType.DEPOSIT)), listOf(relation(ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE)), 1000L..1999).netExpense)
        assertEquals(0L, monthlyTotals(listOf(transaction(2, TransactionType.DEPOSIT)), listOf(relation(ConsistencyRelationType.REFUND_CANDIDATE)), 1000L..1999).confirmedRefund)
    }
}
