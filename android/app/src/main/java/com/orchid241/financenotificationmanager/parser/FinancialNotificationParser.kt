package com.orchid241.financenotificationmanager.parser

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity

/** 금융 앱별 알림 형식을 공통 거래 구조로 변환하는 Parser 계약. */
interface FinancialNotificationParser {
    fun supports(packageName: String): Boolean
    fun parse(raw: RawNotificationEntity): ParseResult
}
