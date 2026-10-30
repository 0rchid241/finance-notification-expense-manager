package com.orchid241.financenotificationmanager.parser

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungWalletNotificationParserTest {
    private val parser = SamsungWalletNotificationParser()

    private fun raw(
        title: String? = "3,800원 결제 완료",
        text: String? = "삼성 월렛 포인트 75원을 사용하여 컴포즈커피 북한산보국문점에서 결제했어요.",
        packageName: String = SupportedFinancialApps.SAMSUNG_WALLET,
    ) = RawNotificationEntity(
        id = 17,
        notificationKey = "wallet-notification",
        packageName = packageName,
        title = title,
        text = text,
        postedAt = 1_761_696_000_000L,
        receivedAt = 1_761_696_001_000L,
    )

    @Test fun parsesPaymentFromScreenshot() {
        val tx = (parser.parse(raw()) as ParseResult.Success).transaction
        assertEquals(TransactionType.WITHDRAWAL, tx.transactionType)
        assertEquals(3800L, tx.amount)
        assertEquals("컴포즈커피 북한산보국문점", tx.counterparty)
        assertEquals("", tx.accountLast4)
        assertEquals(null, tx.balance)
        assertEquals("삼성 월렛", tx.source)
        assertEquals(1_761_696_000_000L, tx.occurredAt)
    }

    @Test fun parsesWithoutPoints() {
        val tx = (parser.parse(raw("12,000원 결제 완료", "가상상점에서 결제했어요.")) as ParseResult.Success).transaction
        assertEquals(12000L, tx.amount)
        assertEquals("가상상점", tx.counterparty)
    }

    @Test fun pointAccrualIsNotDeposit() {
        assertEquals(ParseResult.Ignored, parser.parse(raw("포인트 18원 적립됨", "컴포즈커피 북한산보국문점")))
    }

    @Test fun unknownAndMalformedMessagesDoNotBecomeTransactions() {
        assertEquals(ParseResult.Failure, parser.parse(raw("삼성 월렛", "알림 2개")))
        assertEquals(ParseResult.Failure, parser.parse(raw("3,800원 결제 취소", "가상상점에서 결제했어요.")))
        assertEquals(ParseResult.Failure, parser.parse(raw(text = "이벤트 안내")))
        assertEquals(ParseResult.Failure, parser.parse(raw(title = null)))
        assertEquals(ParseResult.Failure, parser.parse(raw(text = null)))
        for (title in listOf("0원 결제 완료", "3,80원 결제 완료", "-300원 결제 완료", "abc원 결제 완료", "9223372036854775808원 결제 완료")) {
            assertEquals(title, ParseResult.Failure, parser.parse(raw(title)))
        }
    }

    @Test fun wrongPackageIsRejectedAndRegistryIncludesWallet() {
        assertEquals(ParseResult.Failure, parser.parse(raw(packageName = SupportedFinancialApps.KAKAO_BANK)))
        val registry = FinancialNotificationParserRegistry()
        assertTrue(registry.supportsPackage(SupportedFinancialApps.SAMSUNG_WALLET))
        assertTrue(registry.parse(raw()) is ParseResult.Success)
    }
}
