package com.orchid241.financenotificationmanager.ui

import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity

data class TransactionConsistencyUiState(
    val duplicateCandidate: Boolean = false,
    val internalTransferCandidate: Boolean = false,
)

internal fun consistencyByTransaction(
    candidates: List<ConsistencyCandidateEntity>,
): Map<Long, TransactionConsistencyUiState> {
    val result = mutableMapOf<Long, TransactionConsistencyUiState>()

    candidates.forEach { candidate ->
        listOf(candidate.firstTransactionId, candidate.secondTransactionId).forEach { transactionId ->
            val current = result[transactionId] ?: TransactionConsistencyUiState()
            result[transactionId] = when (candidate.relationType) {
                ConsistencyRelationType.DUPLICATE_CANDIDATE -> current.copy(duplicateCandidate = true)
                ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE -> current.copy(internalTransferCandidate = true)
            }
        }
    }

    return result
}
