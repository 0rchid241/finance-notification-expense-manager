package com.orchid241.financenotificationmanager.mockwallet

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity
import com.orchid241.financenotificationmanager.parser.*

/** 실제 삼성월렛 규격이 아닌 개발 전용 합성 포맷. 실제 패키지는 절대 지원하지 않는다. */
class SyntheticWalletParser : FinancialNotificationParser {
    override fun supports(packageName: String) = packageName == PACKAGE

    fun fragment(raw: RawNotificationEntity): WalletFragment? {
        if (!supports(raw.packageName) || raw.notificationKey.isBlank() || raw.notificationKey.length > 200) return null
        val header = raw.title?.split('|') ?: return null
        if (header.size != 5 || header[0] != "[TEST] wallet-v1" ||
            !header[1].matches(Regex("[A-Za-z0-9_-]{1,100}"))) return null
        val time = header[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val type = when (header[3]) { "OUT" -> TransactionType.WITHDRAWAL; "IN" -> TransactionType.DEPOSIT; else -> return null }
        if (header[4] != "payment" || raw.text.isNullOrBlank() || raw.text.length > 1000) return null
        val fields = linkedMapOf<String, String>()
        for (entry in raw.text.split(';')) {
            val pair = entry.split('=', limit = 2)
            if (pair.size != 2 || pair[0] !in setOf("amount", "merchant", "account", "balance") ||
                pair[1].isBlank() || fields.put(pair[0], pair[1]) != null) return null
        }
        val amount = fields["amount"]?.let { it.toLongOrNull()?.takeIf { n -> n > 0 } ?: return null }
        val balance = fields["balance"]?.let { it.toLongOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        val account = fields["account"]?.also { if (!it.matches(Regex("[0-9]{4}"))) return null }
        return WalletFragment(WalletEventId(raw.packageName, raw.notificationKey, header[1], time, type), amount, fields["merchant"], account, balance)
    }

    override fun parse(raw: RawNotificationEntity): ParseResult = fragment(raw)?.toResult(raw.id) ?: ParseResult.Failure

    companion object {
        const val PACKAGE = "dev.fixture.wallet"
        const val SOURCE = "[TEST] 삼성월렛 합성 알림"
    }
}

data class WalletEventId(val packageName: String, val notificationKey: String, val eventId: String, val occurredAt: Long, val type: TransactionType)
data class WalletFragment(val identity: WalletEventId, val amount: Long?, val merchant: String?, val account: String?, val balance: Long?) {
    fun toResult(rawId: Long): ParseResult {
        val value = amount ?: return ParseResult.Failure
        val name = merchant ?: return ParseResult.Failure
        return ParseResult.Success(ParsedFinancialTransaction(rawId, identity.type, value, "[TEST] $name", account.orEmpty(), balance, SyntheticWalletParser.SOURCE, identity.occurredAt))
    }
}
