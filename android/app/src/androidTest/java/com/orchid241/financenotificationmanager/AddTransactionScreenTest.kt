package com.orchid241.financenotificationmanager

import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.orchid241.financenotificationmanager.data.ManualTransactionInput
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.ui.AddTransactionScreen
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import org.junit.Assert.*
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddTransactionScreenTest {
    @Before fun keepScreenOn() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun savesDepositWithoutOptionalFields() {
        var saved: ManualTransactionInput? = null
        var returnedAt: Long? = null
        compose.setContent {
            FinanceNotificationManagerTheme {
                AddTransactionScreen(onBack = {}, onSave = { saved = it }, onSaved = { returnedAt = it })
            }
        }
        compose.onNodeWithText("입금").performClick()
        compose.onNodeWithText("금액").performTextInput("1,000")
        compose.onNodeWithText("상대방").performTextInput("가상인물")
        compose.onNodeWithText("거래 저장").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(TransactionType.DEPOSIT, saved!!.transactionType)
            assertEquals(1000L, saved!!.amount)
            assertEquals("가상인물", saved!!.counterparty)
            assertEquals("", saved!!.accountLast4)
            assertNull(saved!!.balance)
            assertEquals(saved!!.occurredAt, returnedAt)
        }
    }

    @Test fun invalidAmountKeepsFormAndDoesNotSave() {
        var saved = false
        compose.setContent {
            FinanceNotificationManagerTheme {
                AddTransactionScreen(onBack = {}, onSave = { saved = true }, onSaved = {})
            }
        }
        compose.onNodeWithText("금액").performTextInput("0")
        compose.onNodeWithText("상대방").performTextInput("가상인물")
        compose.onNodeWithText("거래 저장").performScrollTo().performClick()
        compose.onNodeWithText("금액은 0보다 큰 정수로 입력해 주세요.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertFalse(saved) }
    }
}
