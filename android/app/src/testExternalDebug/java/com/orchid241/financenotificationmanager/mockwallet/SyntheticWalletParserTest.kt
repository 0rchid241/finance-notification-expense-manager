package com.orchid241.financenotificationmanager.mockwallet

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity
import com.orchid241.financenotificationmanager.parser.*
import org.junit.Assert.*
import org.junit.Test

class SyntheticWalletParserTest {
    private val parser = SyntheticWalletParser()
    private fun raw(text: String? = "amount=1000;merchant=가상상점", title: String? = "[TEST] wallet-v1|a|1000|OUT|payment") =
        RawNotificationEntity(7, "key", SyntheticWalletParser.PACKAGE, title, text, 1000, 1001)

    @Test fun parsesCompleteWithoutInventingBalanceOrAccount() {
        val tx = (parser.parse(raw()) as ParseResult.Success).transaction
        assertEquals(7L, tx.rawNotificationId); assertEquals(1000L, tx.amount)
        assertEquals("[TEST] 가상상점", tx.counterparty); assertNull(tx.balance); assertEquals("", tx.accountLast4)
        assertEquals(SyntheticWalletParser.SOURCE, tx.source)
    }
    @Test fun supportsSyntheticPackageOnlyAndRegistryRemainsKakaoOnly() {
        assertFalse(parser.supports("com.samsung.android.spay"))
        val registry = FinancialNotificationParserRegistry()
        assertFalse(registry.supportsPackage(SyntheticWalletParser.PACKAGE))
        assertFalse(registry.supportsPackage("com.samsung.android.spay"))
        assertTrue(registry.supportsPackage(SupportedFinancialApps.KAKAO_BANK))
        assertTrue(FinancialNotificationParserRegistry(listOf(parser)).parse(raw()) is ParseResult.Success)
    }
    @Test fun partialAmountOrMerchantNeverCreatesTransaction() {
        for (text in listOf("amount=1000", "merchant=가상상점", "balance=0")) assertEquals(ParseResult.Failure, parser.parse(raw(text)))
    }
    @Test fun rejectsEmptyPromotionMalformedOrOverflowValues() {
        for (text in listOf(null, "", " ", "promotion=쿠폰", "amount=0;merchant=가상", "amount=-1;merchant=가상", "amount=999999999999999999999;merchant=가상",
            "amount=1000;merchant=", "amount=1000;amount=2000;merchant=가상", "amount=1000;merchant=가상;account=12345", "amount=1000;merchant=가상;balance=-1")) {
            assertEquals(text, ParseResult.Failure, parser.parse(raw(text)))
        }
    }
    @Test fun rejectsMissingOrUntrustedEventIdentityAndWrongPackage() {
        for (title in listOf(null, "실제 월렛 추측", "[TEST] wallet-v1||1000|OUT|payment", "[TEST] wallet-v1|a|0|OUT|payment", "[TEST] wallet-v1|a|1000|OTHER|payment")) {
            assertNull(parser.fragment(raw(title = title)))
        }
        assertEquals(ParseResult.Failure, parser.parse(raw().copy(packageName = "com.samsung.android.spay")))
        assertNull(parser.fragment(raw().copy(notificationKey = "")))
    }
    @Test fun depositAndOptionalFieldsRetainRealFixtureValues() {
        val tx = (parser.parse(raw("amount=1000;merchant=가상;balance=9000;account=0001", "[TEST] wallet-v1|a|1000|IN|payment")) as ParseResult.Success).transaction
        assertEquals(TransactionType.DEPOSIT, tx.transactionType); assertEquals(9000L, tx.balance); assertEquals("0001", tx.accountLast4)
    }
}
