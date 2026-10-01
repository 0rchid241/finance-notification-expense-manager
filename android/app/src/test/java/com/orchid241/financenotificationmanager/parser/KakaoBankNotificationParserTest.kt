package com.orchid241.financenotificationmanager.parser

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoBankNotificationParserTest {
    private val parser = KakaoBankNotificationParser()
    private fun raw(title: String? = "출금 500,000원", text: String? = "입출금통장(1234) → 테스트은행 가상인물\n잔액 353,000원") =
        RawNotificationEntity(42, "test-key", SupportedFinancialApps.KAKAO_BANK, title, text, 1000, 2000)

    @Test fun withdrawalExtractsAllFields() {
        assertEquals(ParseResult.Success(ParsedFinancialTransaction(42, TransactionType.WITHDRAWAL, 500000, "테스트은행 가상인물", "1234", 353000, "카카오뱅크", 1000)), parser.parse(raw()))
    }

    @Test fun depositExtractsAllFields() {
        assertEquals(ParseResult.Success(ParsedFinancialTransaction(42, TransactionType.DEPOSIT, 500000, "가상인물", "5678", 735838, "카카오뱅크", 1000)), parser.parse(raw("입금 500,000원", "가상인물 → 입출금통장(5678)\n잔액 735,838원")))
    }

    @Test fun supportsAllLineEndings() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            assertEquals(parser.parse(raw()), parser.parse(raw(text = raw().text!!.replace("\n", newline))))
        }
    }

    @Test fun nullTitleFails() { assertEquals(ParseResult.Failure, parser.parse(raw(title = null))) }
    @Test fun nullTextFails() { assertEquals(ParseResult.Failure, parser.parse(raw(text = null))) }
    @Test fun malformedAmountsFail() {
        for (amount in listOf("50,00", "-100", "1.5", "abc", "", "0", "01", "1,0000", "1,00,000", "9223372036854775808")) {
            assertEquals(amount, ParseResult.Failure, parser.parse(raw(title = "출금 ${amount}원")))
        }
    }
    @Test fun malformedBalancesFail() {
        for (balance in listOf("35,30", "-1", "9223372036854775808", "abc")) {
            assertEquals(ParseResult.Failure, parser.parse(raw(text = "입출금통장(1234) → 가상인물\n잔액 ${balance}원")))
        }
    }
    @Test fun unexpectedBodyFails() {
        for (text in listOf("프로모션 안내", "입출금통장(1234) → 가상인물", "입출금통장(1234) → 가상인물\n잔액 10원\n추가 내용", "가상인물 → 입출금통장(1234)\n잔액 10원", "입출금통장(123) → 가상인물\n잔액 10원", "입출금통장(1234) → \n잔액 10원", "입출금통장(1234) → 가상 → 인물\n잔액 10원")) {
            assertEquals(text, ParseResult.Failure, parser.parse(raw(text = text)))
        }
    }
    @Test fun unsupportedPackageFails() {
        assertEquals(ParseResult.Failure, parser.parse(raw().copy(packageName = "com.example.other")))
    }
    @Test fun zeroBalanceAndUngroupedAmountSucceed() {
        val result = parser.parse(raw(title = "출금 1000원", text = "입출금통장(0001) → 가상인물\n잔액 0원")) as ParseResult.Success
        assertEquals(1000L, result.transaction.amount)
        assertEquals(0L, result.transaction.balance)
        assertEquals("0001", result.transaction.accountLast4)
    }
    @Test fun unsupportedTitleFails() {
        assertEquals(ParseResult.Failure, parser.parse(raw(title = "출금 취소 500,000원")))
    }
}
