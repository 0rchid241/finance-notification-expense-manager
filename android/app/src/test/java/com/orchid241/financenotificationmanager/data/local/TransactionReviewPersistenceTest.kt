package com.orchid241.financenotificationmanager.data.local

import androidx.room.Room
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.ManualTransactionInput
import com.orchid241.financenotificationmanager.data.monthlyTotals
import com.orchid241.financenotificationmanager.parser.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TransactionReviewPersistenceTest {
    @Test fun confirmedAndRejectedReviewsSurviveReopenAndEngineRerun() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "review-persistence.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val db = open()
        val before: List<ConsistencyCandidateEntity>
        try {
            val repository = FinancialTransactionRepository(db)
            for ((index, amount) in listOf(10000L, 20000L, 30000L).withIndex()) {
                val first = repository.addManualTransaction(ManualTransactionInput(1000, TransactionType.WITHDRAWAL, amount, "가상매장$index", "0001"))
                val second = repository.addManualTransaction(ManualTransactionInput(1100, if (index == 1) TransactionType.DEPOSIT else TransactionType.WITHDRAWAL, amount, "가상매장$index", "0001"))
                val candidate = db.consistencyCandidateDao().getAll().single { setOf(it.firstTransactionId, it.secondTransactionId) == setOf(first, second) }
                repository.reviewCandidate(candidate.id, index != 2)
            }
            before = db.consistencyCandidateDao().getAll()
        } finally { db.close() }
        val reopened = open()
        try {
            assertEquals(before, reopened.consistencyCandidateDao().getAll())
            FinancialTransactionRepository(reopened).recalculateConsistency()
            assertEquals(before, reopened.consistencyCandidateDao().getAll())
            assertEquals(2, before.count { it.reviewStatus == CandidateReviewStatus.CONFIRMED })
            assertEquals(1, before.count { it.reviewStatus == CandidateReviewStatus.REJECTED })
            val totals = monthlyTotals(reopened.financialTransactionDao().getAll(), reopened.consistencyCandidateDao().getAll(), 0L..2000)
            assertEquals(70000L, totals.netExpense)
            assertEquals(10000L, totals.duplicateExcludedWithdrawal)
            assertEquals(20000L, totals.confirmedRefund)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
