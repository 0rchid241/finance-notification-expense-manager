package com.orchid241.financenotificationmanager

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.ui.TransactionListScreen
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import org.junit.Rule
import org.junit.Test

class TransactionListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyStateExplainsNotificationAccess() {
        compose.setContent { FinanceNotificationManagerTheme { TransactionListScreen(emptyList()) } }
        compose.onNodeWithText("금융 알림 지출 관리").assertIsDisplayed()
        compose.onNodeWithText("아직 기록된 거래가 없습니다.", substring = true).assertIsDisplayed()
    }

    @Test fun withdrawalShowsKoreanLabelsFormattedMoneyAndMaskedAccount() {
        compose.setContent { FinanceNotificationManagerTheme { TransactionListScreen(listOf(transaction())) } }
        compose.onNodeWithText("카카오뱅크 · 출금").assertIsDisplayed()
        compose.onNodeWithText("500,000원").assertIsDisplayed()
        compose.onNodeWithText("****1234").assertIsDisplayed()
        compose.onNodeWithText("353,000원").assertIsDisplayed()
        compose.onNodeWithText("가상인물").assertIsDisplayed()
    }

    @Test fun newDepositReplacesEmptyStateAutomatically() {
        val transactions = mutableStateOf(emptyList<FinancialTransactionEntity>())
        compose.setContent { FinanceNotificationManagerTheme { TransactionListScreen(transactions.value) } }
        compose.runOnIdle { transactions.value = listOf(transaction().copy(transactionType = TransactionType.DEPOSIT)) }
        compose.onNodeWithText("카카오뱅크 · 입금").assertIsDisplayed()
        compose.onNodeWithText("+500,000원").assertIsDisplayed()
    }

    private fun transaction() = FinancialTransactionEntity(1, 1, TransactionType.WITHDRAWAL, 500000, "가상인물", "1234", 353000, "카카오뱅크", 1790832300000, 1790832300000)
}
