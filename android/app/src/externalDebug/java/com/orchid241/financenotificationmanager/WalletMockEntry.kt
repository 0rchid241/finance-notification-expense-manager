package com.orchid241.financenotificationmanager

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.orchid241.financenotificationmanager.mockwallet.WalletMockActivity

@Composable
internal fun WalletMockEntry() {
    val context = LocalContext.current
    TextButton(onClick = { context.startActivity(Intent(context, WalletMockActivity::class.java)) }) {
        Text("[TEST] 삼성월렛 합성 알림 검증 · 별도 DB")
    }
}
