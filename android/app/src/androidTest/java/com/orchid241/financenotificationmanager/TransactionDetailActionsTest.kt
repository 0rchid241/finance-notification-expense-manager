package com.orchid241.financenotificationmanager

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.local.CandidateReviewStatus
import com.orchid241.financenotificationmanager.data.local.ConsistencyCandidateEntity
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.ui.*
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import org.junit.Assert.*
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TransactionDetailActionsTest {
    @Before fun keepScreenOn() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun transaction(id: Long = 1) = FinancialTransactionEntity(id, null, TransactionType.WITHDRAWAL, 1000, "가상매장", "0001", null, "수동 입력", 1000, 1000)

    @Test fun deleteRequiresConfirmationAndCancelKeepsTransaction() {
        var deleted = false
        compose.setContent {
            FinanceNotificationManagerTheme {
                TransactionsScreen(listOf(transaction()), emptyMap(), emptyMap(), false, false, TransactionFilter.ALL, {}, 1, {}, {}, onDeleteTransaction = { deleted = true })
            }
        }
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("거래를 삭제할까요?").assertIsDisplayed()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("거래 상세").assertIsDisplayed()
        compose.onNodeWithText("삭제").performClick()
        compose.onNode(hasText("삭제") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test fun confirmedAndRejectedRelationsHaveDifferentUI() {
        val candidates = mutableStateOf(listOf(ConsistencyCandidateEntity(1, 1, 2, ConsistencyRelationType.DUPLICATE_CANDIDATE, "사유", 1000)))
        compose.setContent {
            FinanceNotificationManagerTheme {
                TransactionsScreen(
                    listOf(transaction(), transaction(2)), consistencyByTransaction(candidates.value), emptyMap(), false, false,
                    TransactionFilter.ALL, {}, 1, {}, {}, candidates = candidates.value,
                    onReviewCandidate = { _, confirmed -> candidates.value = candidates.value.map { it.copy(reviewStatus = if (confirmed) CandidateReviewStatus.CONFIRMED else CandidateReviewStatus.REJECTED) } },
                )
            }
        }
        compose.onNodeWithTag("transaction-detail").performScrollToNode(hasText("중복으로 확정"))
        compose.onNodeWithText("중복으로 확정").performClick()
        compose.onNodeWithText("중복으로 확정").assertDoesNotExist()
        compose.onNodeWithTag("transaction-detail").performScrollToNode(hasText("연결 거래가 집계에서 제외됩니다."))
        compose.onNodeWithText("연결 거래가 집계에서 제외됩니다.").assertIsDisplayed()
        compose.onNodeWithTag("transaction-detail").performScrollToNode(hasText("후보 아님"))
        compose.onNodeWithText("후보 아님").performClick()
        compose.onNodeWithText("후보 아님").assertDoesNotExist()
        compose.onAllNodesWithText("확정된 중복").assertCountEquals(0)
    }

    @Test fun editFormStartsWithExistingValuesAndCallsUpdateCallback() {
        var savedAmount: Long? = null
        compose.setContent {
            FinanceNotificationManagerTheme {
                AddTransactionScreen({}, { savedAmount = it.amount }, {}, initialTransaction = transaction())
            }
        }
        compose.onNodeWithText("거래 수정").assertIsDisplayed()
        compose.onNodeWithText("금액").performTextReplacement("2000")
        compose.onNodeWithText("거래 저장").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2000L, savedAmount) }
    }
}
