package com.orchid241.financenotificationmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionListScreen(
    transactions: List<FinancialTransactionEntity>,
    consistency: Map<Long, TransactionConsistencyUiState> = emptyMap(),
    loading: Boolean = false,
    failed: Boolean = false,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("금융 알림 지출 관리", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("최근 거래", style = MaterialTheme.typography.titleMedium)
                    Text("지원되는 금융 알림이 자동으로 기록됩니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (transactions.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                failed -> "거래를 불러오지 못했습니다. 앱을 다시 열어 주세요."
                                loading -> "거래를 불러오는 중입니다."
                                else -> "아직 기록된 거래가 없습니다.\n시스템 설정에서 알림 접근 권한을 허용한 뒤 금융 알림을 기다려 주세요."
                            },
                            modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            items(transactions, key = { it.id }) { transaction ->
                TransactionCard(transaction, consistency[transaction.id])
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: FinancialTransactionEntity,
    consistency: TransactionConsistencyUiState?,
) {
    val deposit = transaction.transactionType == TransactionType.DEPOSIT
    val accent = if (deposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${transaction.source} · ${transaction.transactionType.label}", color = accent, style = MaterialTheme.typography.titleMedium)
            Text((if (deposit) "+" else "") + money(transaction.amount), color = accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            if (consistency?.duplicateCandidate == true) {
                Text("⚠ 중복 거래 의심", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            if (consistency?.internalTransferCandidate == true) {
                Text("↔ 내부이체 후보", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }

            DetailRow("상대방", transaction.counterparty)
            DetailRow("계좌", "****${transaction.accountLast4}")
            DetailRow("잔액", money(transaction.balance))
            Text(SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(transaction.occurredAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

internal fun money(amount: Long): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"

@Preview(showBackground = true)
@Composable
private fun TransactionsPreview() {
    FinanceNotificationManagerTheme(dynamicColor = false) {
        TransactionListScreen(
            transactions = listOf(
                FinancialTransactionEntity(1, 1, TransactionType.WITHDRAWAL, 500000, "테스트은행 가상인물", "1234", 353000, "카카오뱅크", 1790832300000, 1790832300000),
                FinancialTransactionEntity(2, 2, TransactionType.DEPOSIT, 500000, "가상인물", "5678", 735838, "카카오뱅크", 1790832300000, 1790832300000),
            ),
            consistency = mapOf(
                1L to TransactionConsistencyUiState(duplicateCandidate = true),
                2L to TransactionConsistencyUiState(internalTransferCandidate = true),
            ),
        )
    }
}
