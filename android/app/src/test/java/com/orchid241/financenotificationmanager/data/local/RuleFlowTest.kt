package com.orchid241.financenotificationmanager.data.local

import androidx.room.Room
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.parser.SupportedFinancialApps
import com.orchid241.financenotificationmanager.rules.RuleConditionType
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
class RuleFlowTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FinancialTransactionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).build()
        repository = FinancialTransactionRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun collect(
        title: String = "출금 500,000원",
        text: String = "입출금통장(1234) → 테스트은행 가상인물\n잔액 353,000원",
        time: Long = 1000,
    ) = repository.collectNotification(
        notificationKey = "key-$time",
        packageName = SupportedFinancialApps.KAKAO_BANK,
        title = title,
        text = text,
        postedAt = time,
        receivedAt = time + 1,
    )

    @Test
    fun amountRuleIsSavedAndAppliedToExistingTransaction() = runBlocking {
        collect()
        repository.addAmountRule("큰 지출", 100_000, "큰 지출이 발생했어요.")

        val rule = repository.observeUserRules().first().single()
        val match = repository.observeRuleMatches().first().single()
        val transaction = repository.observeAll().first().single()

        assertEquals(RuleConditionType.EXPENSE_AMOUNT_AT_LEAST, rule.conditionType)
        assertEquals(100_000L, rule.amountThreshold)
        assertEquals(rule.id, match.ruleId)
        assertEquals(transaction.id, match.transactionId)
        assertEquals("큰 지출", match.ruleName)
    }

    @Test
    fun savedCounterpartyRuleIsAppliedToNewTransaction() = runBlocking {
        val ruleId = repository.addCounterpartyRule("상대방 알림", "테스트은행", "지정 상대방 거래가 발생했어요.")
        collect()

        val match = repository.observeRuleMatches().first().single()
        assertEquals(ruleId, match.ruleId)
        assertEquals("상대방 알림", match.ruleName)
        assertTrue(match.message.contains("지정 상대방"))
    }

    @Test
    fun nonMatchingRuleDoesNotCreateMatch() = runBlocking {
        repository.addAmountRule("초고액", 1_000_000, "초고액 지출입니다.")
        collect()

        assertTrue(repository.observeRuleMatches().first().isEmpty())
    }
}
