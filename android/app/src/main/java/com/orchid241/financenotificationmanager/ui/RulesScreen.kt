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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orchid241.financenotificationmanager.data.local.UserRuleEntity
import com.orchid241.financenotificationmanager.rules.RuleConditionType
import java.text.NumberFormat
import java.util.Locale

private enum class RuleDraftType(val label: String) {
    AMOUNT("금액 기준"),
    COUNTERPARTY("상대방 키워드"),
}

@Composable
fun RulesScreen(
    rules: List<UserRuleEntity>,
    onAddAmountRule: (Long) -> Unit,
    onAddCounterpartyRule: (String) -> Unit,
    onToggleRule: (Long, Boolean) -> Unit,
    onDeleteRule: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var draftTypeName by rememberSaveable { mutableStateOf(RuleDraftType.AMOUNT.name) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var keywordText by rememberSaveable { mutableStateOf("") }
    val draftType = RuleDraftType.valueOf(draftTypeName)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("규칙", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "내가 원하는 거래만 따로 알려줘요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { adding = !adding }) {
                    Text(if (adding) "닫기" else "+ 규칙 추가")
                }
            }
        }

        if (adding) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("새 규칙", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RuleTypePill(
                                label = RuleDraftType.AMOUNT.label,
                                selected = draftType == RuleDraftType.AMOUNT,
                                onClick = { draftTypeName = RuleDraftType.AMOUNT.name },
                            )
                            RuleTypePill(
                                label = RuleDraftType.COUNTERPARTY.label,
                                selected = draftType == RuleDraftType.COUNTERPARTY,
                                onClick = { draftTypeName = RuleDraftType.COUNTERPARTY.name },
                            )
                        }

                        when (draftType) {
                            RuleDraftType.AMOUNT -> {
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = it.filter(Char::isDigit) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("지출 금액") },
                                    placeholder = { Text("예: 50000") },
                                    singleLine = true,
                                )
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = amountText.toLongOrNull()?.let { it > 0 } == true,
                                    onClick = {
                                        amountText.toLongOrNull()?.takeIf { it > 0 }?.let(onAddAmountRule)
                                        amountText = ""
                                        adding = false
                                    },
                                ) { Text("저장") }
                            }

                            RuleDraftType.COUNTERPARTY -> {
                                OutlinedTextField(
                                    value = keywordText,
                                    onValueChange = { keywordText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("상대방 이름 키워드") },
                                    placeholder = { Text("예: 편의점") },
                                    singleLine = true,
                                )
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = keywordText.isNotBlank(),
                                    onClick = {
                                        keywordText.trim().takeIf { it.isNotEmpty() }?.let(onAddCounterpartyRule)
                                        keywordText = ""
                                        adding = false
                                    },
                                ) { Text("저장") }
                            }
                        }
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                Text(
                    text = "아직 만든 규칙이 없어요.\n금액이나 상대방 이름을 기준으로 알림 규칙을 만들어보세요.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { enabled -> onToggleRule(rule.id, enabled) },
                    onDelete = { onDeleteRule(rule.id) },
                )
            }
        }

        item {
            Text(
                text = "중복 의심과 내부이체 후보는 시스템이 자동으로 판정하며 사용자 규칙과 별도로 관리돼요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: UserRuleEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = ruleConditionText(rule),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Text(
                text = rule.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "삭제",
                modifier = Modifier.clickable(onClick = onDelete).padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RuleTypePill(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private fun ruleConditionText(rule: UserRuleEntity): String = when (rule.conditionType) {
    RuleConditionType.EXPENSE_AMOUNT_AT_LEAST ->
        "${NumberFormat.getIntegerInstance(Locale.KOREA).format(rule.amountThreshold ?: 0)}원 이상 지출"
    RuleConditionType.COUNTERPARTY_CONTAINS ->
        "상대방 이름에 '${rule.keyword.orEmpty()}' 포함"
}
