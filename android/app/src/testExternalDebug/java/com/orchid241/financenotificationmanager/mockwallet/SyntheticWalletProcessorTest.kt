package com.orchid241.financenotificationmanager.mockwallet

import androidx.room.Room
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.local.*
import com.orchid241.financenotificationmanager.parser.SupportedFinancialApps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], manifest=Config.NONE)
class SyntheticWalletProcessorTest {
    private lateinit var db: AppDatabase
    private lateinit var processor: SyntheticWalletProcessor
    private fun raw(event: String="a", text: String="amount=1000;merchant=가상상점", received: Long=1001) =
        RawNotificationEntity(notificationKey="[TEST]-key",packageName=SyntheticWalletParser.PACKAGE,title="[TEST] wallet-v1|$event|1000|OUT|payment",text=text,postedAt=1000,receivedAt=received)
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).build()
        processor=SyntheticWalletProcessor(db)
    }
    @After fun close() { db.close() }

    @Test fun singleRawParseTransactionConsistencyAndRulesUseExistingPipeline() = runBlocking {
        val repository=FinancialTransactionRepository(db)
        repository.addAmountRule("[TEST] 규칙",1000,"[TEST] 경고")
        assertEquals("PROCESSED",processor.receive(raw()))
        assertEquals("PROCESSED",processor.receive(raw("b",received=1002)))
        val transactions=db.financialTransactionDao().getAll()
        assertEquals(2,transactions.size)
        assertEquals(2,db.ruleMatchDao().observeAll().first().size)
        assertTrue(db.consistencyCandidateDao().getAll().isNotEmpty())
        assertTrue(transactions.all { it.source.startsWith("[TEST]") && it.counterparty.startsWith("[TEST]") && it.balance==null })
        transactions.forEach { assertNotNull(db.rawNotificationDao().getById(it.rawNotificationId!!)) }
    }
    @Test fun updatesPreserveBothRawRowsAndLinkCompletingCallback() = runBlocking {
        val first=raw(text="amount=1000")
        val second=raw(text="merchant=가상상점",received=1100)
        assertEquals("MOCK_WAITING",processor.receive(first))
        assertEquals("PROCESSED",processor.receive(second))
        val raws=db.rawNotificationDao().getAll()
        assertEquals(listOf(first.text,second.text),raws.map { it.text })
        assertEquals(raws.last().id,db.financialTransactionDao().getAll().single().rawNotificationId)
    }
    @Test fun completedReceiptSurvivesCacheExpiryRestartAndTransactionDeletion() = runBlocking {
        processor.receive(raw())
        assertEquals("MOCK_DUPLICATE",processor.receive(raw(received=7000)))
        processor=SyntheticWalletProcessor(db)
        assertEquals("MOCK_DUPLICATE",processor.receive(raw(received=8000)))
        FinancialTransactionRepository(db).deleteTransaction(db.financialTransactionDao().getAll().single().id)
        assertEquals("MOCK_DUPLICATE",SyntheticWalletProcessor(db).receive(raw(received=9000)))
        assertEquals(4,db.rawNotificationDao().getAll().size)
        assertTrue(db.financialTransactionDao().getAll().isEmpty())
    }
    @Test fun incompleteRestartCannotMergeAcrossProcessLifetimes() = runBlocking {
        processor.receive(raw(text="amount=1000"))
        assertEquals("MOCK_WAITING",SyntheticWalletProcessor(db).receive(raw(text="merchant=가상상점",received=1100)))
        assertTrue(db.financialTransactionDao().getAll().isEmpty())
    }
    @Test fun repeatedInvalidOrConflictingInputNeverCreatesFakeTransactions() = runBlocking {
        assertEquals("MOCK_INVALID",processor.receive(raw(text="")))
        processor.receive(raw(text="amount=1000"))
        assertEquals("MOCK_CONFLICT",processor.receive(raw(text="amount=2000;merchant=가상",received=1100)))
        assertEquals(3,db.rawNotificationDao().getAll().size)
        assertTrue(db.financialTransactionDao().getAll().isEmpty())
    }
    @Test fun simultaneousDuplicateCallbacksAreSerialized() = runBlocking {
        val statuses=(1..8).map { async(Dispatchers.Default) { processor.receive(raw()) } }.awaitAll()
        assertEquals(1,statuses.count { it=="PROCESSED" })
        assertEquals(7,statuses.count { it=="MOCK_DUPLICATE" })
        assertEquals(8,db.rawNotificationDao().getAll().size)
        assertEquals(1,db.financialTransactionDao().getAll().size)
    }
    @Test fun databaseTransactionSerializesSeparateProcessorInstances() = runBlocking {
        val statuses=(1..4).map { async(Dispatchers.Default) { SyntheticWalletProcessor(db).receive(raw()) } }.awaitAll()
        assertEquals(1,statuses.count { it=="PROCESSED" })
        assertEquals(1,db.financialTransactionDao().getAll().size)
    }
    @Test fun allExternalJsonScenariosMatchExpectedStatusAndCounts() = runBlocking {
        val scenarios=WalletScenarios.load(File("src/externalDebug/assets/wallet_fixture_scenarios.json").readText())
        assertEquals(12,scenarios.size)
        scenarios.forEach { scenario ->
            val report=WalletScenarios.run(db,scenario)
            assertTrue("${scenario.id}: $report",report.passed)
            if(scenario.transactions>0) assertEquals(scenario.transactions,report.ruleMatches)
            if(scenario.id=="distinct") assertTrue(report.candidates>0)
        }
    }
    @Test fun kakaoCollectionRemainsSeparateAndSupported() = runBlocking {
        val repository=FinancialTransactionRepository(db)
        assertNull(repository.collectNotification("key",SyntheticWalletParser.PACKAGE,"anything","anything",1000,1001))
        assertTrue(db.rawNotificationDao().getAll().isEmpty())
        repository.collectNotification("kakao",SupportedFinancialApps.KAKAO_BANK,"출금 10,000원","입출금통장(1234) → 가상매장\n잔액 50,000원",1000,1001)
        val tx=db.financialTransactionDao().getAll().single()
        assertEquals("카카오뱅크",tx.source); assertEquals(50000L,tx.balance)
    }
    @Test fun failedSharedEngineWriteRollsBackAndRetryIsNotLostToCache() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_mock BEFORE INSERT ON financial_transactions BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { processor.receive(raw()); fail("저장 실패 예상") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals("PENDING",db.rawNotificationDao().getAll().single().processingStatus)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_mock")
        assertEquals("PROCESSED",processor.receive(raw()))
        assertEquals(1,db.financialTransactionDao().getAll().size)
    }
    @Test fun fileDatabaseReopenRetainsReceiptAndExistingData() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val name="mock-reopen-test.db"
        context.deleteDatabase(name)
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        var disk=open()
        try {
            SyntheticWalletProcessor(disk).receive(raw())
            disk.close(); disk=open()
            assertEquals("MOCK_DUPLICATE",SyntheticWalletProcessor(disk).receive(raw(received=10000)))
            assertEquals(1,disk.financialTransactionDao().getAll().size)
            assertEquals(2,disk.rawNotificationDao().getAll().size)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
