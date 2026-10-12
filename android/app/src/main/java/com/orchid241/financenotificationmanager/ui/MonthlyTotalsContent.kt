package com.orchid241.financenotificationmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchid241.financenotificationmanager.data.MonthlyTotals
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MonthlyTotalsContent(totals: MonthlyTotals, modifier: Modifier = Modifier) {
    fun money(value: Long) = NumberFormat.getIntegerInstance(Locale.KOREA).format(value) + "원"
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("순지출", style = MaterialTheme.typography.bodyMedium)
        Text(money(totals.netExpense), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("총 출금 ${money(totals.totalWithdrawal)}", style = MaterialTheme.typography.bodyMedium)
            Text("총 입금 ${money(totals.totalDeposit)}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("환불 확정 ${money(totals.confirmedRefund)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("중복 제외 ${money(totals.duplicateExcluded)} (출금 ${money(totals.duplicateExcludedWithdrawal)} · 입금 ${money(totals.duplicateExcludedDeposit)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
