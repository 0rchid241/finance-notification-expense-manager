package com.orchid241.financenotificationmanager.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchid241.financenotificationmanager.ui.theme.FinanceStatusColors

/** 거래 상태를 짧고 일관된 형태로 표시한다. */
enum class StatusChipType(
    val label: String,
) {
    DUPLICATE("중복 의심"),
    INTERNAL_TRANSFER("내부이체 후보"),
    REFUND("취소/환불 후보"),
    RULE_WARNING("규칙 경고"),
    CONFIRMED_DUPLICATE("확정된 중복"),
    CONFIRMED_REFUND("확정된 환불"),
    CONFIRMED_TRANSFER("확정된 내부이체"),
    NEEDS_REVIEW("재확인 필요"),
}

@Composable
fun StatusChip(
    type: StatusChipType,
    modifier: Modifier = Modifier,
) {
    val accent = when (type) {
        StatusChipType.DUPLICATE -> FinanceStatusColors.Duplicate
        StatusChipType.INTERNAL_TRANSFER -> FinanceStatusColors.InternalTransfer
        StatusChipType.REFUND -> FinanceStatusColors.Success
        StatusChipType.RULE_WARNING -> FinanceStatusColors.Warning
        StatusChipType.CONFIRMED_DUPLICATE, StatusChipType.CONFIRMED_REFUND, StatusChipType.CONFIRMED_TRANSFER -> FinanceStatusColors.Success
        StatusChipType.NEEDS_REVIEW -> FinanceStatusColors.Warning
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.10f),
        contentColor = accent,
    ) {
        Text(
            text = type.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = accent,
        )
    }
}
