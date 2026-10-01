package com.orchid241.financenotificationmanager.parser

object SupportedFinancialApps {
    const val KAKAO_BANK = "com.kakaobank.channel"
    private val packages = setOf(KAKAO_BANK)

    fun supports(packageName: String): Boolean = packageName in packages
}
