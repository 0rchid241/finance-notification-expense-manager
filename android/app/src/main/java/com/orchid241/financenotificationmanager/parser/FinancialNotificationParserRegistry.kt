package com.orchid241.financenotificationmanager.parser

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity

/**
 * packageName에 맞는 금융사 Parser를 선택한다.
 * 새 금융사를 추가할 때는 Parser 구현을 만들고 기본 목록에 등록하면 된다.
 */
class FinancialNotificationParserRegistry(
    private val parsers: List<FinancialNotificationParser> = listOf(
        KakaoBankNotificationParser(),
    ),
) {
    fun supportsPackage(packageName: String): Boolean =
        parsers.any { it.supports(packageName) }

    fun parse(raw: RawNotificationEntity): ParseResult {
        val parser = parsers.firstOrNull { it.supports(raw.packageName) }
            ?: return ParseResult.Failure
        return parser.parse(raw)
    }
}
