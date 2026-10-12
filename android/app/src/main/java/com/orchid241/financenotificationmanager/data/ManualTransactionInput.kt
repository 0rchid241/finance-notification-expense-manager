package com.orchid241.financenotificationmanager.data

import com.orchid241.financenotificationmanager.parser.TransactionType

data class ManualTransactionInput(
    val occurredAt: Long,
    val transactionType: TransactionType,
    val amount: Long,
    val counterparty: String,
    val accountLast4: String = "",
    val balance: Long? = null,
) {
    fun validated(): ManualTransactionInput {
        require(occurredAt >= 0) { "날짜와 시간을 확인해 주세요." }
        require(amount > 0) { "금액은 0보다 큰 정수로 입력해 주세요." }
        require(counterparty.isNotBlank()) { "상대방을 입력해 주세요." }
        require(accountLast4.isBlank() || Regex("[0-9]{4}").matches(accountLast4.trim())) {
            "계좌 끝자리는 숫자 4자리로 입력해 주세요."
        }
        require(balance == null || balance >= 0) { "잔액은 0 이상의 정수로 입력해 주세요." }
        return copy(counterparty = counterparty.trim(), accountLast4 = accountLast4.trim())
    }
}

/** 입력 문자열을 숫자로 변환할 때 잘못된 쉼표나 소수·음수를 임의로 보정하지 않는다. */
fun manualTransactionInput(
    occurredAt: Long,
    type: TransactionType,
    amountText: String,
    counterparty: String,
    accountLast4: String,
    balanceText: String,
): ManualTransactionInput {
    fun number(text: String, message: String): Long {
        val trimmed = text.trim()
        require(Regex("(?:0|[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)").matches(trimmed)) { message }
        return requireNotNull(trimmed.replace(",", "").toLongOrNull()) { message }
    }
    return ManualTransactionInput(
        occurredAt, type,
        number(amountText, "금액을 올바른 정수로 입력해 주세요."),
        counterparty, accountLast4,
        if (balanceText.isBlank()) null else number(balanceText, "잔액을 올바른 정수로 입력해 주세요."),
    ).validated()
}
