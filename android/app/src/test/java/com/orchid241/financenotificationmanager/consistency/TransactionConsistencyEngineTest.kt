package com.orchid241.financenotificationmanager.consistency

import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionConsistencyEngineTest {
    private val engine = TransactionConsistencyEngine()

    private fun transaction(
        id: Long,
        type: TransactionType = TransactionType.WITHDRAWAL,
        amount: Long = 10_000,
        counterparty: String = "편의점",
        accountLast4: String = "1234",
        source: String = "카카오뱅크",
        occurredAt: Long = 1_000_000,
    ) = FinancialTransactionEntity(
        id = id,
        rawNotificationId = id,
        transactionType = type,
        amount = amount,
        counterparty = counterparty,
        accountLast4 = accountLast4,
        balance = 100_000,
        source = source,
        occurredAt = occurredAt,
        createdAt = occurredAt,
    )

    @Test fun sameTransactionWithinTwoMinutesBecomesDuplicateCandidate() {
        val first = transaction(id = 1, counterparty = "GS25 정릉점")
        val second = transaction(id = 2, counterparty = "gs25정릉점", occurredAt = 1_100_000)

        val result = engine.findCandidates(listOf(first, second))

        assertEquals(1, result.size)
        assertEquals(ConsistencyRelationType.DUPLICATE_CANDIDATE, result.single().relationType)
        assertEquals(1L, result.single().firstTransactionId)
        assertEquals(2L, result.single().secondTransactionId)
    }

    @Test fun duplicateOutsideTimeWindowIsIgnored() {
        val first = transaction(id = 1)
        val second = transaction(id = 2, occurredAt = 1_120_001)

        assertTrue(engine.findCandidates(listOf(first, second)).isEmpty())
    }

    @Test fun sameAmountButDifferentCounterpartyIsNotDuplicate() {
        val first = transaction(id = 1, counterparty = "편의점")
        val second = transaction(id = 2, counterparty = "카페")

        assertTrue(engine.findCandidates(listOf(first, second)).isEmpty())
    }

    @Test fun oppositeDirectionsAcrossDifferentAccountsBecomeInternalTransferCandidate() {
        val withdrawal = transaction(
            id = 1,
            type = TransactionType.WITHDRAWAL,
            accountLast4 = "1234",
            counterparty = "내 KB 계좌",
        )
        val deposit = transaction(
            id = 2,
            type = TransactionType.DEPOSIT,
            accountLast4 = "5678",
            counterparty = "내 카카오뱅크 계좌",
            source = "KB",
            occurredAt = 1_240_000,
        )

        val result = engine.findCandidates(listOf(withdrawal, deposit))

        assertEquals(1, result.size)
        assertEquals(ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE, result.single().relationType)
    }

    @Test fun oppositeDirectionsWithDifferentAmountsAreIgnored() {
        val withdrawal = transaction(id = 1, type = TransactionType.WITHDRAWAL, amount = 10_000, accountLast4 = "1234")
        val deposit = transaction(id = 2, type = TransactionType.DEPOSIT, amount = 20_000, accountLast4 = "5678")

        assertTrue(engine.findCandidates(listOf(withdrawal, deposit)).isEmpty())
    }

    @Test fun oppositeDirectionsOnSameAccountAndSameCounterpartyBecomeRefundCandidate() {
        val withdrawal = transaction(
            id = 1,
            type = TransactionType.WITHDRAWAL,
            amount = 8_000,
            counterparty = "스타벅스 정릉점",
            accountLast4 = "1234",
            occurredAt = 1_000_000,
        )
        val deposit = transaction(
            id = 2,
            type = TransactionType.DEPOSIT,
            amount = 8_000,
            counterparty = "스타벅스정릉점",
            accountLast4 = "1234",
            occurredAt = 1_000_000 + 24 * 60 * 60 * 1000L,
        )

        val result = engine.findCandidates(listOf(withdrawal, deposit))

        assertEquals(1, result.size)
        assertEquals(ConsistencyRelationType.REFUND_CANDIDATE, result.single().relationType)
        assertEquals(1L, result.single().firstTransactionId)
        assertEquals(2L, result.single().secondTransactionId)
    }

    @Test fun refundWithDifferentCounterpartyIsIgnored() {
        val withdrawal = transaction(id = 1, type = TransactionType.WITHDRAWAL, counterparty = "스타벅스")
        val deposit = transaction(
            id = 2,
            type = TransactionType.DEPOSIT,
            counterparty = "김철수",
            occurredAt = 1_000_000 + 60_000,
        )

        assertTrue(engine.findCandidates(listOf(withdrawal, deposit)).isEmpty())
    }

    @Test fun refundWithDifferentAccountIsInternalTransferCandidateNotRefund() {
        val withdrawal = transaction(id = 1, type = TransactionType.WITHDRAWAL, accountLast4 = "1234")
        val deposit = transaction(
            id = 2,
            type = TransactionType.DEPOSIT,
            accountLast4 = "5678",
            occurredAt = 1_120_000,
        )

        val result = engine.findCandidates(listOf(withdrawal, deposit))

        assertEquals(1, result.size)
        assertEquals(ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE, result.single().relationType)
    }

    @Test fun refundAfterFourteenDaysIsIgnored() {
        val withdrawal = transaction(id = 1, type = TransactionType.WITHDRAWAL)
        val deposit = transaction(
            id = 2,
            type = TransactionType.DEPOSIT,
            occurredAt = 1_000_000 + 14 * 24 * 60 * 60 * 1000L + 1,
        )

        assertTrue(engine.findCandidates(listOf(withdrawal, deposit)).isEmpty())
    }

    @Test fun depositBeforeWithdrawalIsNotRefund() {
        val deposit = transaction(id = 1, type = TransactionType.DEPOSIT, occurredAt = 1_000_000)
        val withdrawal = transaction(id = 2, type = TransactionType.WITHDRAWAL, occurredAt = 1_060_000)

        assertTrue(engine.findCandidates(listOf(deposit, withdrawal)).isEmpty())
    }

    @Test fun blankAccountsDoNotCreateInternalTransferOrRefundCandidates() {
        val withdrawal = transaction(id = 1, type = TransactionType.WITHDRAWAL, accountLast4 = "")
        val deposit = transaction(id = 2, type = TransactionType.DEPOSIT, accountLast4 = "", occurredAt = 1_060_000)

        assertTrue(engine.findCandidates(listOf(withdrawal, deposit)).isEmpty())
    }

    @Test fun internalTransferOutsideFiveMinutesIsIgnored() {
        val withdrawal = transaction(id = 1, type = TransactionType.WITHDRAWAL, accountLast4 = "1234", counterparty = "내 계좌 A")
        val deposit = transaction(
            id = 2,
            type = TransactionType.DEPOSIT,
            accountLast4 = "5678",
            counterparty = "내 계좌 B",
            occurredAt = 1_300_001,
        )

        assertTrue(engine.findCandidates(listOf(withdrawal, deposit)).isEmpty())
    }

    @Test fun oneTransactionCreatesNoCandidate() {
        assertTrue(engine.findCandidates(listOf(transaction(id = 1))).isEmpty())
    }
}
