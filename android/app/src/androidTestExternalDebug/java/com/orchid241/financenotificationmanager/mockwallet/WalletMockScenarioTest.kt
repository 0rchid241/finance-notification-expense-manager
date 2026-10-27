package com.orchid241.financenotificationmanager.mockwallet

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 별도 테스트 DB의 합성 데이터만 쓰며 사용자의 가계부 DB에는 접근하지 않는다. */
class WalletMockScenarioTest {
    @get:Rule val compose = createAndroidComposeRule<WalletMockActivity>()
    @Before fun keepScreenOn() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    @Test fun runsAllTwelveSyntheticScenariosInDeveloperApp() {
        compose.onNodeWithText("전체 시나리오 실행").performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("검증 결과 성공 12 / 12").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("검증 결과 성공 12 / 12").assertIsDisplayed()
    }
}
