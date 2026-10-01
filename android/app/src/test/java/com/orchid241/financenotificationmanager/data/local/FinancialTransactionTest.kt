package com.orchid241.financenotificationmanager.data.local

import androidx.room.Room
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.RawProcessingStatus
import com.orchid241.financenotificationmanager.parser.ParseResult
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
class FinancialTransactionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FinancialTransactionRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = FinancialTransactionRepository(database)
    }
    @After fun tearDown() { database.close() }

    private suspend fun collect(title: String? = "출금 500,000원", text: String? = "입출금통장(1234) → 테스트은행 가상인물\n잔액 353,000원", packageName: String = SupportedFinancialApps.KAKAO_BANK, time: Long = 1000) =
        repository.collectNotification("same-key", packageName, title, text, time, 2000)

    @Test fun daoPreservesFieldsAndOrdersByTimeThenId() = runBlocking {
        val rawId = database.rawNotificationDao().insert(RawNotificationEntity(notificationKey = "key", packageName = SupportedFinancialApps.KAKAO_BANK, title = null, text = null, postedAt = 1000, receivedAt = 1001))
        val event = FinancialTransactionEntity(rawNotificationId = rawId, transactionType = TransactionType.DEPOSIT, amount = 500000, counterparty = "가상인물", accountLast4 = "0001", balance = 735838, source = "카카오뱅크", occurredAt = 1000, createdAt = 2000)
        val dao = database.financialTransactionDao()
        val firstId = dao.insert(event)
        val olderId = dao.insert(event.copy(occurredAt = 900))
        val tieId = dao.insert(event)
        assertEquals(listOf(event.copy(id = tieId), event.copy(id = firstId), event.copy(id = olderId, occurredAt = 900)), dao.observeAll().first())
    }
    @Test fun successLinksRawAndMarksProcessed() = runBlocking {
        assertTrue(collect() is ParseResult.Success)
        val raw = database.rawNotificationDao().getAll().single()
        val transaction = repository.observeAll().first().single()
        assertEquals(RawProcessingStatus.PROCESSED, raw.processingStatus)
        assertEquals(raw.id, transaction.rawNotificationId)
        assertEquals(raw.postedAt, transaction.occurredAt)
        assertEquals(500000L, transaction.amount)
        assertTrue(transaction.createdAt > 0)
    }
    @Test fun nullAndMalformedRawAreRetainedAsFailures() = runBlocking {
        assertEquals(ParseResult.Failure, collect(title = null))
        assertEquals(ParseResult.Failure, collect(text = null))
        assertEquals(ParseResult.Failure, collect(text = "예상하지 못한 본문"))
        val raws = database.rawNotificationDao().getAll()
        assertEquals(3, raws.size)
        assertNull(raws[0].title)
        assertNull(raws[1].text)
        assertTrue(raws.all { it.processingStatus == RawProcessingStatus.PARSE_FAILED })
        assertTrue(repository.observeAll().first().isEmpty())
    }
    @Test fun unsupportedNewNotificationIsIgnoredAndExistingRawIsKept() = runBlocking {
        val old = RawNotificationEntity(notificationKey = "old", packageName = "com.example.other", title = null, text = null, postedAt = 1, receivedAt = 2)
        val id = database.rawNotificationDao().insert(old)
        assertNull(collect(packageName = old.packageName))
        assertEquals(listOf(old.copy(id = id)), database.rawNotificationDao().getAll())
        assertTrue(repository.observeAll().first().isEmpty())
    }
    @Test fun repeatedCallbacksCreateSeparateRawAndTransactionRows() = runBlocking {
        collect()
        collect()
        val raw = database.rawNotificationDao().getAll()
        val transactions = repository.observeAll().first()
        assertEquals(2, raw.size)
        assertEquals(2, transactions.size)
        assertEquals(raw.map { it.id }.toSet(), transactions.map { it.rawNotificationId }.toSet())
    }
    @Test fun transactionWriteFailureRetainsPendingRawAndNextEventCanSucceed() = runBlocking {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_transaction BEFORE INSERT ON financial_transactions BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try {
            collect()
            fail("Expected transaction failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            // Raw commit is independent of the failed structured transaction.
        }
        assertEquals("PENDING", database.rawNotificationDao().getAll().single().processingStatus)
        assertTrue(repository.observeAll().first().isEmpty())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_transaction")
        collect()
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test fun statusUpdateFailureRollsBackStructuredTransactionOnly() = runBlocking {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_status BEFORE UPDATE ON raw_notifications BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try {
            collect()
            fail("Expected status failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            // The transaction insert must roll back together with its status update.
        }
        assertEquals("PENDING", database.rawNotificationDao().getAll().single().processingStatus)
        assertTrue(repository.observeAll().first().isEmpty())
    }
}
