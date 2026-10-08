package com.orchid241.financenotificationmanager.parser

object SupportedFinancialApps {
    const val KAKAO_BANK = "com.kakaobank.channel"
    const val TOSS = "viva.republica.toss"
    const val KB_STAR_BANKING = "com.kbstar.kbbank"
    const val SAMSUNG_WALLET = "com.samsung.android.spay"

    // 알림 샘플 수집을 위한 임시 허용 목록.
    // 샘플 확보 후 실제 지원 Parser가 구현된 앱만 남기거나 별도 수집 모드로 분리한다.
    private val packages = setOf(
        KAKAO_BANK,
        TOSS,
        KB_STAR_BANKING,
        SAMSUNG_WALLET,
    )

    fun supports(packageName: String): Boolean = packageName in packages
}
