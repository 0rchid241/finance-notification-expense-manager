package com.orchid241.financenotificationmanager.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orchid241.financenotificationmanager.data.ManualTransactionInput
import com.orchid241.financenotificationmanager.data.manualTransactionInput
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.parser.TransactionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    onSave: suspend (ManualTransactionInput) -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialTransaction: FinancialTransactionEntity? = null,
) {
    var occurredAt by rememberSaveable(initialTransaction?.id) { mutableLongStateOf(initialTransaction?.occurredAt ?: System.currentTimeMillis()) }
    var type by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.transactionType ?: TransactionType.WITHDRAWAL) }
    var amount by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.amount?.toString().orEmpty()) }
    var counterparty by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.counterparty.orEmpty()) }
    var account by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.accountLast4.orEmpty()) }
    var balance by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.balance?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row {
            TextButton(onClick = onBack, enabled = !saving) { Text("뒤로") }
            Text(if (initialTransaction == null) "거래 추가" else "거래 수정", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(if (initialTransaction == null) "직접 입력한 거래도 정합성 확인과 사용자 규칙이 적용돼요." else "수정하면 후보와 규칙을 다시 확인해요. 원본 알림은 그대로 남습니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !saving, onClick = {
                val date = Calendar.getInstance().apply { timeInMillis = occurredAt }
                DatePickerDialog(context, { _, year, month, day ->
                    occurredAt = (date.clone() as Calendar).apply {
                        set(year, month, day)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text(SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(occurredAt)) }
            TextButton(enabled = !saving, onClick = {
                val date = Calendar.getInstance().apply { timeInMillis = occurredAt }
                TimePickerDialog(context, { _, hour, minute ->
                    occurredAt = (date.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }, date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE), true).show()
            }) { Text(SimpleDateFormat("HH:mm", Locale.KOREA).format(occurredAt)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionType.entries.forEach { entry ->
                FilterChip(selected = type == entry, onClick = { type = entry }, enabled = !saving, label = { Text(entry.label) })
            }
        }
        InputField("금액", amount, { amount = it }, saving, numeric = true)
        InputField("상대방", counterparty, { counterparty = it }, saving)
        InputField("계좌 끝 4자리 (선택)", account, { account = it }, saving, numeric = true)
        InputField("잔액 (선택)", balance, { balance = it }, saving, numeric = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = !saving,
            onClick = {
                val input = try {
                    manualTransactionInput(occurredAt, type, amount, counterparty, account, balance)
                } catch (invalid: IllegalArgumentException) {
                    error = invalid.message
                    null
                }
                if (input != null) {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            onSave(input)
                            onSaved(input.occurredAt)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "저장하지 못했어요. 입력 내용을 확인하고 다시 시도해 주세요."
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        ) { Text(if (saving) "저장 중…" else "거래 저장") }
    }
}

@Composable
private fun InputField(label: String, value: String, onChange: (String) -> Unit, saving: Boolean, numeric: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, enabled = !saving,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
    )
}
