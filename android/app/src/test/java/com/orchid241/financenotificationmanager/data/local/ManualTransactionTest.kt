package com.orchid241.financenotificationmanager.data.local

import androidx.room.Room
import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.ManualTransactionInput
import com.orchid241.financenotificationmanager.parser.SupportedFinancialApps
import com.orchid241.financenotificationmanager.parser.TransactionType
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
class ManualTransactionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: FinancialTransactionRepository
    private fun input(type: TransactionType = TransactionType.WITHDRAWAL, account: String = "", time: Long = 1000) =
        ManualTransactionInput(time, type, 10000, " 가상매장 ", account)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = FinancialTransactionRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun manualWithoutOptionalFieldsHasNoFakeRaw() = runBlocking {
        val id = repository.addManualTransaction(input())
        val stored = repository.observeAll().first().single()
        assertEquals(id, stored.id)
        assertNull(stored.rawNotificationId)
        assertNull(stored.balance)
        assertEquals("", stored.accountLast4)
        assertEquals("가상매장", stored.counterparty)
        assertEquals("수동 입력", stored.source)
        assertEquals(1000L, stored.occurredAt)
        assertTrue(db.rawNotificationDao().getAll().isEmpty())
    }
    @Test fun optionalFieldsPreserveZeroBalanceAndLeadingZeros() = runBlocking {
        repository.addManualTransaction(input(account = "0001").copy(balance = 0))
        val stored = repository.observeAll().first().single()
        assertEquals("0001", stored.accountLast4)
        assertEquals(0L, stored.balance)
    }
    @Test fun manualTransactionsUseBothEngines() = runBlocking {
        val ruleId = repository.addAmountRule("큰 지출", 1000, "확인")
        val first = repository.addManualTransaction(input(account = "0001"))
        val second = repository.addManualTransaction(input(account = "0001", time = 1100))
        val candidate = repository.observeConsistencyCandidates().first().single()
        assertEquals(ConsistencyRelationType.DUPLICATE_CANDIDATE, candidate.relationType)
        assertEquals(setOf(first, second), setOf(candidate.firstTransactionId, candidate.secondTransactionId))
        val matches = repository.observeRuleMatches().first()
        assertEquals(setOf(first, second), matches.map { it.transactionId }.toSet())
        assertTrue(matches.all { it.ruleId == ruleId })
    }
    @Test fun manualAndNotificationTransactionsCanHaveInternalTransferCandidate() = runBlocking {
        repository.collectNotification("key", SupportedFinancialApps.KAKAO_BANK, "출금 10,000원", "입출금통장(1234) → 가상매장\n잔액 50,000원", 1000, 1001)
        repository.addManualTransaction(input(TransactionType.DEPOSIT, "5678", 1100))
        assertEquals(ConsistencyRelationType.INTERNAL_TRANSFER_CANDIDATE, repository.observeConsistencyCandidates().first().single().relationType)
        assertEquals(1, db.rawNotificationDao().getAll().size)
    }
    @Test fun missingAccountsDoNotBecomeInternalTransferOrRefundCandidates() = runBlocking {
        repository.addManualTransaction(input())
        repository.addManualTransaction(input(TransactionType.DEPOSIT, time = 1100))
        assertTrue(repository.observeConsistencyCandidates().first().isEmpty())
    }
    @Test fun invalidInputDoesNotWriteAnyRows() = runBlocking {
        for (invalid in listOf(input().copy(amount = 0), input().copy(counterparty = " "), input().copy(accountLast4 = "123"), input().copy(balance = -1), input().copy(occurredAt = -1))) {
            try { repository.addManualTransaction(invalid); fail("잘못된 입력을 거부해야 함") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(repository.observeAll().first().isEmpty())
        assertTrue(db.rawNotificationDao().getAll().isEmpty())
    }
    @Test fun ruleFailureRollsBackManualTransactionAndCandidates() = runBlocking {
        repository.addManualTransaction(input(account = "0001"))
        repository.addAmountRule("큰 지출", 1000, "확인")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_match BEFORE INSERT ON rule_matches BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repository.addManualTransaction(input(account = "0001", time = 1100)); fail("저장 실패가 발생해야 함") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(1, repository.observeAll().first().size)
        assertTrue(repository.observeConsistencyCandidates().first().isEmpty())
        assertEquals(1, repository.observeRuleMatches().first().size)
    }
}
