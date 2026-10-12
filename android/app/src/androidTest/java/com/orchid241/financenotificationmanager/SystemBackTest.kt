package com.orchid241.financenotificationmanager

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** 화면 이동만 확인하며 실제 사용자 DB에는 거래를 추가하거나 삭제하지 않는다. */
class SystemBackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun pressSystemBack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }

    @Test fun transactionRuleAndSettingsTabsReturnHome() {
        for (tab in listOf("거래", "규칙", "설정")) {
            compose.onNode(hasText(tab) and hasClickAction()).performClick()
            pressSystemBack()
            compose.onNode(hasText("홈") and hasClickAction()).assertIsSelected()
        }
    }

    @Test fun addScreenReturnsListThenHome() {
        compose.onNode(hasText("거래") and hasClickAction()).performClick()
        compose.onNodeWithText("+ 거래 추가").performClick()
        compose.onNodeWithText("거래 추가").assertIsDisplayed()
        pressSystemBack()
        compose.onNodeWithText("+ 거래 추가").assertIsDisplayed()
        pressSystemBack()
        compose.onNode(hasText("홈") and hasClickAction()).assertIsSelected()
    }

    @Test fun homeLeavesSystemBackToAndroid() {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }
}
