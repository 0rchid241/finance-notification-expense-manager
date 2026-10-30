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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SamsungWalletFlowTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FinancialTransactionRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = FinancialTransactionRepository(database)
    }

    @After fun tearDown() { database.close() }

    @Test fun paymentPersistsRawAndTransaction() = runBlocking {
        val result = repository.collectNotification(
            notificationKey = "wallet-payment",
            packageName = SupportedFinancialApps.SAMSUNG_WALLET,
            title = "3,800원 결제 완료",
            text = "삼성 월렛 포인트 75원을 사용하여 컴포즈커피 북한산보국문점에서 결제했어요.",
            postedAt = 1000,
            receivedAt = 2000,
        )
        assertTrue(result is ParseResult.Success)
        val raw = database.rawNotificationDao().getAll().single()
        val tx = repository.observeAll().first().single()
        assertEquals(RawProcessingStatus.PROCESSED, raw.processingStatus)
        assertEquals(raw.id, tx.rawNotificationId)
        assertEquals(TransactionType.WITHDRAWAL, tx.transactionType)
        assertEquals(3800L, tx.amount)
        assertEquals("", tx.accountLast4)
        assertEquals(null, tx.balance)
    }

    @Test fun pointAccrualRetainedButExcludedFromTransactions() = runBlocking {
        val result = repository.collectNotification(
            notificationKey = "wallet-points",
            packageName = SupportedFinancialApps.SAMSUNG_WALLET,
            title = "포인트 18원 적립됨",
            text = "컴포즈커피 북한산보국문점",
            postedAt = 1000,
            receivedAt = 2000,
        )
        assertEquals(ParseResult.Ignored, result)
        assertEquals(RawProcessingStatus.IGNORED, database.rawNotificationDao().getAll().single().processingStatus)
        assertTrue(repository.observeAll().first().isEmpty())
    }
}
