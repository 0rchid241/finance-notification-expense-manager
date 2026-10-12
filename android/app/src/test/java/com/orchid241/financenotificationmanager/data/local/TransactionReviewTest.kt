package com.orchid241.financenotificationmanager.data.local

import androidx.room.Room
import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.ManualTransactionInput
import com.orchid241.financenotificationmanager.data.monthlyTotals
import com.orchid241.financenotificationmanager.parser.SupportedFinancialApps
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.ui.consistencyByTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TransactionReviewTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: FinancialTransactionRepository
    private fun input(time: Long = 1000, type: TransactionType = TransactionType.WITHDRAWAL, amount: Long = 10000) =
        ManualTransactionInput(time, type, amount, "가상매장", "0001", 50000)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = FinancialTransactionRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun pair(refund: Boolean = false): ConsistencyCandidateEntity {
        repository.addManualTransaction(input())
        repository.addManualTransaction(input(1100, if (refund) TransactionType.DEPOSIT else TransactionType.WITHDRAWAL))
        return repository.observeConsistencyCandidates().first().single()
    }
    private suspend fun totals() = monthlyTotals(db.financialTransactionDao().getAll(), db.consistencyCandidateDao().getAll(), 0L..2000L)

    @Test fun editsAllFieldsAndPreservesRawSourceAndCreationTime() = runBlocking {
        repository.collectNotification("key", SupportedFinancialApps.KAKAO_BANK, "출금 10,000원", "입출금통장(1234) → 가상매장\n잔액 50,000원", 1000, 1001)
        val old = db.financialTransactionDao().getAll().single()
        val raw = db.rawNotificationDao().getAll()
        val replacement = input(1200, TransactionType.DEPOSIT, 20000).copy(counterparty = "변경 상대방", accountLast4 = "0002", balance = null)
        repository.updateTransaction(old.id, replacement)
        assertEquals(old.copy(occurredAt = 1200, transactionType = TransactionType.DEPOSIT, amount = 20000, counterparty = "변경 상대방", accountLast4 = "0002", balance = null), db.financialTransactionDao().getById(old.id))
        assertEquals(raw, db.rawNotificationDao().getAll())
    }
    @Test fun deletionCascadesCandidatesAndRulesButKeepsRaw() = runBlocking {
        repository.addAmountRule("금액", 1000, "확인")
        repeat(2) { repository.collectNotification("key", SupportedFinancialApps.KAKAO_BANK, "출금 10,000원", "입출금통장(1234) → 가상매장\n잔액 50,000원", 1000, 1001) }
        val original = db.rawNotificationDao().getAll()
        val id = db.financialTransactionDao().getAll().first().id
        repository.deleteTransaction(id)
        assertEquals(1, db.financialTransactionDao().getAll().size)
        assertTrue(db.consistencyCandidateDao().getAll().isEmpty())
        assertEquals(1, db.ruleMatchDao().observeAll().first().size)
        assertEquals(original, db.rawNotificationDao().getAll())
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }
    @Test fun duplicateOnlyAffectsTotalsAfterConfirmationAndDoesNotDeleteRows() = runBlocking {
        val candidate = pair()
        assertEquals(20000L, totals().netExpense)
        repository.reviewCandidate(candidate.id, true)
        assertEquals(10000L, totals().netExpense)
        assertEquals(10000L, totals().duplicateExcludedWithdrawal)
        assertEquals(2, db.financialTransactionDao().getAll().size)
        val state = consistencyByTransaction(db.consistencyCandidateDao().getAll()).values.first()
        assertTrue(state.confirmedDuplicate)
        assertFalse(state.duplicateCandidate)
    }
    @Test fun refundConfirmationAffectsNetExpense() = runBlocking {
        val candidate = pair(true)
        assertEquals(10000L, totals().netExpense)
        repository.reviewCandidate(candidate.id, true)
        assertEquals(10000L, totals().confirmedRefund)
        assertEquals(0L, totals().netExpense)
        assertEquals(10000L, totals().totalDeposit)
    }
    @Test fun rejectionSurvivesRecalculationAndDisappearanceThenReturn() = runBlocking {
        val candidate = pair()
        repository.reviewCandidate(candidate.id, false)
        repository.recalculateConsistency()
        repository.updateTransaction(candidate.secondTransactionId, input(1100, amount = 20000))
        assertFalse(db.consistencyCandidateDao().getById(candidate.id)!!.isActive)
        repository.updateTransaction(candidate.secondTransactionId, input(1100))
        val stored = db.consistencyCandidateDao().getById(candidate.id)!!
        assertEquals(CandidateReviewStatus.REJECTED, stored.reviewStatus)
        assertTrue(stored.isActive)
        assertNotNull(stored.reviewedAt)
        assertTrue(consistencyByTransaction(db.consistencyCandidateDao().getAll()).isEmpty())
        assertEquals(20000L, totals().netExpense)
    }
    @Test fun editInvalidatesConfirmationUntilUserReviewsAgain() = runBlocking {
        val candidate = pair()
        repository.reviewCandidate(candidate.id, true)
        repository.updateTransaction(candidate.secondTransactionId, input(1101))
        assertEquals(CandidateReviewStatus.NEEDS_REVIEW, db.consistencyCandidateDao().getById(candidate.id)!!.reviewStatus)
        assertEquals(20000L, totals().netExpense)
        repository.reviewCandidate(candidate.id, true)
        assertEquals(10000L, totals().netExpense)
    }
    @Test fun changedAmountRemovesActiveRelationAndCannotBeConfirmed() = runBlocking {
        val candidate = pair(true)
        repository.reviewCandidate(candidate.id, true)
        repository.updateTransaction(candidate.secondTransactionId, input(1100, TransactionType.DEPOSIT, 20000))
        assertFalse(db.consistencyCandidateDao().getById(candidate.id)!!.isActive)
        assertEquals(0L, totals().confirmedRefund)
        try { repository.reviewCandidate(candidate.id, true); fail("조건 불일치를 확정하면 안 됨") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun balanceOnlyEditPreservesConfirmation() = runBlocking {
        val candidate = pair()
        repository.reviewCandidate(candidate.id, true)
        val old = db.financialTransactionDao().getById(candidate.secondTransactionId)!!
        repository.updateTransaction(old.id, ManualTransactionInput(old.occurredAt, old.transactionType, old.amount, old.counterparty, old.accountLast4, null))
        assertEquals(CandidateReviewStatus.CONFIRMED, db.consistencyCandidateDao().getById(candidate.id)!!.reviewStatus)
        assertEquals(10000L, totals().netExpense)
    }
    @Test fun editReplacesRuleResultsAndDisabledRuleCanBeReenabled() = runBlocking {
        val rule = repository.addAmountRule("금액", 10000, "확인")
        val id = repository.addManualTransaction(input(amount = 20000))
        assertEquals(1, db.ruleMatchDao().observeAll().first().size)
        repository.updateTransaction(id, input(amount = 1000))
        assertTrue(db.ruleMatchDao().observeAll().first().isEmpty())
        repository.setRuleEnabled(rule, false)
        repository.updateTransaction(id, input(amount = 20000))
        repository.setRuleEnabled(rule, true)
        assertEquals(1, db.ruleMatchDao().observeAll().first().size)
    }
    @Test fun conflictingConfirmationsAreRejectedAndRepeatedConfirmationIsSafe() = runBlocking {
        val candidate = pair()
        repository.addManualTransaction(input(1200))
        repository.reviewCandidate(candidate.id, true)
        repository.reviewCandidate(candidate.id, true)
        val overlap = db.consistencyCandidateDao().getAll().first { it.id != candidate.id }
        try { repository.reviewCandidate(overlap.id, true); fail("확정 관계가 겹치면 안 됨") }
        catch (_: IllegalArgumentException) { }
        assertEquals(20000L, totals().netExpense)
        repository.reviewCandidate(candidate.id, false)
        repository.reviewCandidate(overlap.id, true)
        assertEquals(20000L, totals().netExpense)
    }
    @Test fun rejectedPairStaysRejectedWhenRefundDirectionChanges() = runBlocking {
        val candidate = pair(true)
        repository.reviewCandidate(candidate.id, false)
        repository.updateTransaction(candidate.firstTransactionId, input(1200, TransactionType.DEPOSIT))
        repository.updateTransaction(candidate.secondTransactionId, input(1100, TransactionType.WITHDRAWAL))
        val stored = db.consistencyCandidateDao().getAll().single { it.relationType == ConsistencyRelationType.REFUND_CANDIDATE }
        assertEquals(candidate.id, stored.id)
        assertEquals(CandidateReviewStatus.REJECTED, stored.reviewStatus)
        assertTrue(stored.isActive)
    }
    @Test fun failedRuleReevaluationRollsBackEditAndReviewChanges() = runBlocking {
        val candidate = pair()
        repository.addAmountRule("금액", 1000, "확인")
        repository.reviewCandidate(candidate.id, true)
        val before = db.financialTransactionDao().getAll()
        val reviews = db.consistencyCandidateDao().getAll()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_rule BEFORE INSERT ON rule_matches BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repository.updateTransaction(candidate.secondTransactionId, input(1101)); fail("저장 실패가 필요함") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, db.financialTransactionDao().getAll())
        assertEquals(reviews, db.consistencyCandidateDao().getAll())
        assertEquals(2, db.ruleMatchDao().observeAll().first().size)
    }
}
