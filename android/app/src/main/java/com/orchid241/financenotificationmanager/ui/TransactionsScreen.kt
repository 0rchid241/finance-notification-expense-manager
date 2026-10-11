package com.orchid241.financenotificationmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.data.local.RuleMatchEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import com.orchid241.financenotificationmanager.ui.theme.FinanceStatusColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TransactionFilter(val label: String) {
    ALL("전체"),
    EXPENSE("출금"),
    INCOME("입금"),
    ATTENTION("확인 필요"),
}

@Composable
fun TransactionsScreen(
    transactions: List<FinancialTransactionEntity>,
    consistency: Map<Long, TransactionConsistencyUiState>,
    ruleMatches: Map<Long, List<RuleMatchEntity>>,
    loading: Boolean,
    failed: Boolean,
    selectedFilter: TransactionFilter,
    onFilterSelected: (TransactionFilter) -> Unit,
    selectedTransactionId: Long?,
    onTransactionSelected: (Long) -> Unit,
    onDetailBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }

    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.id == id } }
    if (selectedTransaction != null) {
        TransactionDetailScreen(
            transaction = selectedTransaction,
            consistency = consistency[selectedTransaction.id],
            ruleMatches = ruleMatches[selectedTransaction.id].orEmpty(),
            onBack = onDetailBack,
            modifier = modifier,
        )
        return
    }

    val range = monthRange(monthOffset)
    val monthTransactions = transactions.filter { it.occurredAt in range }
    val expense = monthTransactions.filter { it.transactionType != TransactionType.DEPOSIT }.sumOf { it.amount }
    val income = monthTransactions.filter { it.transactionType == TransactionType.DEPOSIT }.sumOf { it.amount }
    val filtered = monthTransactions.filter { transaction ->
        when (selectedFilter) {
            TransactionFilter.ALL -> true
            TransactionFilter.EXPENSE -> transaction.transactionType != TransactionType.DEPOSIT
            TransactionFilter.INCOME -> transaction.transactionType == TransactionType.DEPOSIT
            TransactionFilter.ATTENTION -> {
                val state = consistency[transaction.id]
                state?.duplicateCandidate == true ||
                    state?.internalTransferCandidate == true ||
                    state?.refundCandidate == true ||
                    ruleMatches[transaction.id].orEmpty().isNotEmpty()
            }
        }
    }.sortedByDescending { it.occurredAt }

    val groups = filtered.groupBy { transactionDateKey(it.occurredAt) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = "거래",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clickable { monthOffset -= 1 }.padding(8.dp),
                )
                Text(
                    text = monthTitle(monthOffset),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clickable(enabled = monthOffset < 0) { monthOffset += 1 }.padding(8.dp),
                    color = if (monthOffset < 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AmountSummary("지출", transactionMoney(expense), FinanceStatusColors.Outflow)
                    AmountSummary("입금", transactionMoney(income), FinanceStatusColors.Inflow)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionFilter.entries.forEach { filter ->
                    FilterPill(
                        label = filter.label,
                        selected = selectedFilter == filter,
                        onClick = { onFilterSelected(filter) },
                    )
                }
            }
        }

        when {
            loading -> item { ScreenMessage("거래 내역을 불러오는 중이에요.") }
            failed -> item { ScreenMessage("거래 내역을 불러오지 못했어요.") }
            filtered.isEmpty() -> item { ScreenMessage("이 조건에 맞는 거래가 없어요.") }
            else -> groups.forEach { (date, itemsForDate) ->
                item(key = "date-$date") {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(itemsForDate, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        consistency = consistency[transaction.id],
                        ruleMatches = ruleMatches[transaction.id].orEmpty(),
                        onClick = { onTransactionSelected(transaction.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailScreen(
    transaction: FinancialTransactionEntity,
    consistency: TransactionConsistencyUiState?,
    ruleMatches: List<RuleMatchEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deposit = transaction.transactionType == TransactionType.DEPOSIT
    val amountColor = if (deposit) FinanceStatusColors.Inflow else FinanceStatusColors.Outflow

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
                )
                Text("거래 상세", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = transaction.counterparty.ifBlank { transaction.source },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = (if (deposit) "+" else "-") + transactionMoney(transaction.amount),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (consistency?.duplicateCandidate == true) StatusChip(StatusChipType.DUPLICATE)
                        if (consistency?.internalTransferCandidate == true) StatusChip(StatusChipType.INTERNAL_TRANSFER)
                        if (consistency?.refundCandidate == true) StatusChip(StatusChipType.REFUND)
                        if (ruleMatches.isNotEmpty()) StatusChip(StatusChipType.RULE_WARNING)
                    }
                }
            }
        }

        if (consistency?.refundCandidate == true) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("취소/환불 후보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "같은 계좌에서 같은 상대방·금액의 출금 후 입금 거래가 확인됐어요. 자동 확정하지 않고 확인이 필요한 후보로 표시합니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DetailLine("유형", if (deposit) "입금" else "출금")
                    DetailLine("계좌", if (transaction.accountLast4.isBlank()) "정보 없음" else "****${transaction.accountLast4}")
                    DetailLine("잔액", transactionMoney(transaction.balance))
                    DetailLine("출처", transaction.source)
                    DetailLine("시간", SimpleDateFormat("yyyy년 M월 d일 HH:mm", Locale.KOREA).format(Date(transaction.occurredAt)))
                }
            }
        }

        if (ruleMatches.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("규칙 알림", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ruleMatches.forEach { match ->
                        Text(
                            text = "${match.ruleName} · ${match.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountSummary(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ScreenMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun monthRange(offset: Int): LongRange {
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, offset)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    return start.timeInMillis until end.timeInMillis
}

private fun monthTitle(offset: Int): String {
    val calendar = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
    return SimpleDateFormat("yyyy년 M월", Locale.KOREA).format(calendar.time)
}

private fun transactionDateKey(timestamp: Long): String =
    SimpleDateFormat("M월 d일 E요일", Locale.KOREA).format(Date(timestamp))

private fun transactionMoney(amount: Long): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"
