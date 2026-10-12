package com.orchid241.financenotificationmanager.ui

import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import com.orchid241.financenotificationmanager.data.local.CandidateReviewStatus

data class TransactionConsistencyUiState(
    val duplicateCandidate: Boolean = false,
    val internalTransferCandidate: Boolean = false,
    val refundCandidate: Boolean = false,
    val confirmedDuplicate: Boolean = false,
    val confirmedRefund: Boolean = false,
    val confirmedTransfer: Boolean = false,
    val needsReview: Boolean = false,
)

internal fun consistencyByTransaction(
    candidates: List<ConsistencyCandidateEntity>,
): Map<Long, TransactionConsistencyUiState> {
    val result = mutableMapOf<Long, TransactionConsistencyUiState>()

    candidates.forEach { candidate ->
        if (candidate.reviewStatus == CandidateReviewStatus.REJECTED) return@forEach
        if (!candidate.isActive && candidate.reviewStatus != CandidateReviewStatus.NEEDS_REVIEW) return@forEach
        listOf(candidate.firstTransactionId, candidate.secondTransactionId).forEach { transactionId ->
            val current = result[transactionId] ?: TransactionConsistencyUiState()
            result[transactionId] = if (candidate.reviewStatus == CandidateReviewStatus.CONFIRMED) when (candidate.relationType) {
                ConsistencyRelationType.DUPLICATE_CANDIDATE -> current.copy(confirmedDuplicate = true)
                ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE -> current.copy(confirmedTransfer = true)
                ConsistencyRelationType.REFUND_CANDIDATE -> current.copy(confirmedRefund = true)
            } else if (candidate.reviewStatus == CandidateReviewStatus.NEEDS_REVIEW) current.copy(needsReview = true)
            else when (candidate.relationType) {
                ConsistencyRelationType.DUPLICATE_CANDIDATE -> current.copy(duplicateCandidate = true)
                ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE -> current.copy(internalTransferCandidate = true)
                ConsistencyRelationType.REFUND_CANDIDATE -> current.copy(refundCandidate = true)
            }
        }
    }

    return result
}
