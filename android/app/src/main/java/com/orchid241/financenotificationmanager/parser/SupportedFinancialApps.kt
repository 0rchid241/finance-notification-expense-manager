package com.orchid241.financenotificationmanager.parser

object SupportedFinancialApps {
    const val KAKAO_BANK = "com.kakaobank.channel"
    const val SAMSUNG_WALLET = "com.samsung.android.spay"
    private val packages = setOf(KAKAO_BANK, SAMSUNG_WALLET)

    fun supports(packageName: String): Boolean = packageName in packages
}
