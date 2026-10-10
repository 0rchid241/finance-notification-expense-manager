package com.orchid241.financenotificationmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.data.local.RuleMatchEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.ui.theme.DepositBlue
import com.orchid241.financenotificationmanager.ui.theme.WarningAmber
import com.orchid241.financenotificationmanager.ui.theme.WithdrawalRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    transactions: List<FinancialTransactionEntity>,
    consistency: Map<Long, TransactionConsistencyUiState>,
    ruleMatches: Map<Long, List<RuleMatchEntity>>,
    loading: Boolean,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    val monthRange = currentMonthRange()
    val previousMonthRange = previousMonthRange()
    val monthTransactions = transactions.filter { it.occurredAt in monthRange }
    val previousMonthTransactions = transactions.filter { it.occurredAt in previousMonthRange }

    val expense = monthTransactions
        .filter { it.transactionType != TransactionType.DEPOSIT }
        .sumOf { it.amount }
    val income = monthTransactions
        .filter { it.transactionType == TransactionType.DEPOSIT }
        .sumOf { it.amount }
    val previousExpense = previousMonthTransactions
        .filter { it.transactionType != TransactionType.DEPOSIT }
        .sumOf { it.amount }

    val attentionIds = monthTransactions
        .filter { transaction ->
            val state = consistency[transaction.id]
            state?.duplicateCandidate == true ||
                state?.internalTransferCandidate == true ||
                ruleMatches[transaction.id].orEmpty().isNotEmpty()
        }
        .map { it.id }
        .toSet()

    val duplicateCount = monthTransactions.count { consistency[it.id]?.duplicateCandidate == true }
    val transferCount = monthTransactions.count { consistency[it.id]?.internalTransferCandidate == true }
    val ruleWarningCount = monthTransactions.count { ruleMatches[it.id].orEmpty().isNotEmpty() }
    val recentTransactions = transactions.sortedByDescending { it.occurredAt }.take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "알림 가계부",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = SimpleDateFormat("yyyy년 M월", Locale.KOREA).format(System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MonthlySummaryCard(
                expense = expense,
                previousExpense = previousExpense,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryMetric(
                    label = "지출",
                    value = money(expense),
                    valueColor = WithdrawalRed,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = "입금",
                    value = money(income),
                    valueColor = DepositBlue,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = "확인 필요",
                    value = "${attentionIds.size}건",
                    valueColor = WarningAmber,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (duplicateCount + transferCount + ruleWarningCount > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "주의가 필요한 거래")
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (duplicateCount > 0) {
                                AttentionRow("중복 의심", "${duplicateCount}건", StatusChipType.DUPLICATE)
                            }
                            if (transferCount > 0) {
                                AttentionRow("내부이체 후보", "${transferCount}건", StatusChipType.INTERNAL_TRANSFER)
                            }
                            if (ruleWarningCount > 0) {
                                AttentionRow("규칙 경고", "${ruleWarningCount}건", StatusChipType.RULE_WARNING)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "최근 거래")
        }

        when {
            loading -> item {
                EmptyMessage("거래 내역을 불러오는 중이에요.")
            }

            failed -> item {
                EmptyMessage("거래 내역을 불러오지 못했어요.")
            }

            recentTransactions.isEmpty() -> item {
                EmptyMessage("아직 저장된 거래가 없어요.")
            }

            else -> {
                items(recentTransactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        consistency = consistency[transaction.id],
                        ruleMatches = ruleMatches[transaction.id].orEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    expense: Long,
    previousExpense: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "이번 달 지출",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = money(expense),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (previousExpense > 0) {
                val difference = previousExpense - expense
                val comparisonText = when {
                    difference > 0 -> "지난달보다 ${money(difference)} 적게 사용했어요"
                    difference < 0 -> "지난달보다 ${money(-difference)} 더 사용했어요"
                    else -> "지난달과 같은 금액을 사용했어요"
                }
                Text(
                    text = comparisonText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun AttentionRow(
    label: String,
    count: String,
    type: StatusChipType,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(type)
        Text(
            text = count,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun EmptyMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun currentMonthRange(): LongRange {
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    return start.timeInMillis until end.timeInMillis
}

private fun previousMonthRange(): LongRange {
    val end = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = (end.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    return start.timeInMillis until end.timeInMillis
}

private fun money(amount: Long): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"
