package com.orchid241.financenotificationmanager.ui

import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionConsistencyUiTest {
    @Test
    fun duplicateCandidateMarksBothTransactions() {
        val result = consistencyByTransaction(
            listOf(
                candidate(1, 2, ConsistencyRelationType.DUPLICATE_CANDIDATE),
            ),
        )

        assertTrue(result.getValue(1).duplicateCandidate)
        assertTrue(result.getValue(2).duplicateCandidate)
        assertFalse(result.getValue(1).internalTransferCandidate)
    }

    @Test
    fun differentRelationsAccumulateForSameTransaction() {
        val result = consistencyByTransaction(
            listOf(
                candidate(1, 2, ConsistencyRelationType.DUPLICATE_CANDIDATE),
                candidate(1, 3, ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE),
            ),
        )

        assertTrue(result.getValue(1).duplicateCandidate)
        assertTrue(result.getValue(1).internalTransferCandidate)
        assertTrue(result.getValue(3).internalTransferCandidate)
    }

    private fun candidate(
        firstId: Long,
        secondId: Long,
        type: ConsistencyRelationType,
    ) = ConsistencyCandidateEntity(
        firstTransactionId = firstId,
        secondTransactionId = secondId,
        relationType = type,
        reasonText = "test",
        createdAt = 1000,
    )
}
