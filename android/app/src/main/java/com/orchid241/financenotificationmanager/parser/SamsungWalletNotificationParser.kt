package com.orchid241.financenotificationmanager.parser

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity

/**
 * Minimal Samsung Wallet parser for the observed "N원 결제 완료" notification format.
 * Point accrual is not a cash deposit. Unknown account and balance are not fabricated.
 */
class SamsungWalletNotificationParser : FinancialNotificationParser {
    override fun supports(packageName: String): Boolean =
        packageName == SupportedFinancialApps.SAMSUNG_WALLET

    override fun parse(raw: RawNotificationEntity): ParseResult {
        if (!supports(raw.packageName)) return ParseResult.Failure
        val title = raw.title?.trim() ?: return ParseResult.Failure
        if (pointAccrualTitle.matches(title)) return ParseResult.Ignored

        val titleMatch = paymentTitle.matchEntire(title) ?: return ParseResult.Failure
        val text = raw.text?.trim() ?: return ParseResult.Failure
        val bodyMatch = paymentBody.matchEntire(text) ?: return ParseResult.Failure
        val amount = titleMatch.groupValues[1].replace(",", "").toLongOrNull()
            ?.takeIf { it > 0 } ?: return ParseResult.Failure
        val counterparty = bodyMatch.groupValues[1].trim()
        if (counterparty.isBlank()) return ParseResult.Failure

        return ParseResult.Success(
            ParsedFinancialTransaction(
                rawNotificationId = raw.id,
                transactionType = TransactionType.WITHDRAWAL,
                amount = amount,
                counterparty = counterparty,
                accountLast4 = "",
                balance = null,
                source = "삼성 월렛",
                occurredAt = raw.postedAt,
            ),
        )
    }

    private companion object {
        private val paymentTitle = Regex("""((?:0|[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+))원\s+결제\s*완료""")
        private val pointAccrualTitle = Regex("""포인트\s+(?:0|[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)원\s+적립됨""")
        private val paymentBody = Regex(
            """(?:삼성\s*월렛\s*포인트\s+[0-9][0-9,]*원을\s*사용하여\s+)?(.+?)에서\s+결제했어요\.""",
        )
    }
}
