package com.orchid241.financenotificationmanager.parser

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity

enum class TransactionType(val label: String) {
    WITHDRAWAL("출금"), DEPOSIT("입금"),
}

data class ParsedFinancialTransaction(
    val rawNotificationId: Long,
    val transactionType: TransactionType,
    val amount: Long,
    val counterparty: String,
    val accountLast4: String,
    val balance: Long,
    val source: String,
    val occurredAt: Long,
)

sealed interface ParseResult {
    data class Success(val transaction: ParsedFinancialTransaction) : ParseResult
    data object Failure : ParseResult
}

/** Pure parsing: unknown or incomplete formats never become transactions. */
class KakaoBankNotificationParser {
    fun parse(raw: RawNotificationEntity): ParseResult {
        if (raw.packageName != SupportedFinancialApps.KAKAO_BANK) return ParseResult.Failure
        val title = titlePattern.matchEntire(raw.title?.trim() ?: return ParseResult.Failure)
            ?: return ParseResult.Failure
        val type = if (title.groupValues[1] == "출금") TransactionType.WITHDRAWAL else TransactionType.DEPOSIT
        val amount = number(title.groupValues[2])?.takeIf { it > 0 } ?: return ParseResult.Failure
        val lines = (raw.text ?: return ParseResult.Failure).trim().split(Regex("\\r\\n|\\n|\\r"))
        if (lines.size != 2) return ParseResult.Failure
        val route = lines[0].trim().split('→')
        if (route.size != 2) return ParseResult.Failure
        val accountSide = if (type == TransactionType.WITHDRAWAL) route[0] else route[1]
        val account = accountPattern.matchEntire(accountSide.trim()) ?: return ParseResult.Failure
        val counterparty = (if (type == TransactionType.WITHDRAWAL) route[1] else route[0]).trim()
        if (counterparty.isBlank()) return ParseResult.Failure
        val balanceMatch = balancePattern.matchEntire(lines[1].trim()) ?: return ParseResult.Failure
        val balance = number(balanceMatch.groupValues[1]) ?: return ParseResult.Failure
        return ParseResult.Success(
            ParsedFinancialTransaction(
                rawNotificationId = raw.id,
                transactionType = type,
                amount = amount,
                counterparty = counterparty,
                accountLast4 = account.groupValues[1],
                balance = balance,
                source = "카카오뱅크",
                // Notifications provide no separate transaction time; use their postedAt.
                occurredAt = raw.postedAt,
            ),
        )
    }

    private fun number(value: String): Long? = value.replace(",", "").toLongOrNull()

    private companion object {
        const val MONEY = "(0|[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)"
        val titlePattern = Regex("(출금|입금)\\s+${MONEY}원")
        val balancePattern = Regex("잔액\\s+${MONEY}원")
        val accountPattern = Regex("입출금통장\\(([0-9]{4})\\)")
    }
}
