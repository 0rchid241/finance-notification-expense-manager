package com.orchid241.financenotificationmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.orchid241.financenotificationmanager.ui.theme.WithdrawalRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 거래 한 건을 카드가 아닌 고밀도 리스트 Row로 표현한다.
 * 홈의 최근 거래와 거래 전체 목록에서 동일하게 재사용한다.
 */
@Composable
fun TransactionRow(
    transaction: FinancialTransactionEntity,
    consistency: TransactionConsistencyUiState? = null,
    ruleMatches: List<RuleMatchEntity> = emptyList(),
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val deposit = transaction.transactionType == TransactionType.DEPOSIT
    val amountColor = if (deposit) DepositBlue else WithdrawalRed

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = transaction.counterparty.ifBlank { transaction.source },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${transaction.source} · ${transactionTime(transaction.occurredAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val hasStatus = consistency?.duplicateCandidate == true ||
                    consistency?.internalTransferCandidate == true ||
                    ruleMatches.isNotEmpty()

                if (hasStatus) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (consistency?.duplicateCandidate == true) {
                            StatusChip(StatusChipType.DUPLICATE)
                        }
                        if (consistency?.internalTransferCandidate == true) {
                            StatusChip(StatusChipType.INTERNAL_TRANSFER)
                        }
                        if (ruleMatches.isNotEmpty()) {
                            StatusChip(StatusChipType.RULE_WARNING)
                        }
                    }
                }
            }

            Text(
                text = (if (deposit) "+" else "-") + money(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
        }

        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun transactionTime(timestamp: Long): String =
    SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(timestamp))

private fun money(amount: Long): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"
