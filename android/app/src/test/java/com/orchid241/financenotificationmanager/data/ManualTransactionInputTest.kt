package com.orchid241.financenotificationmanager.data

import com.orchid241.financenotificationmanager.parser.TransactionType
import org.junit.Assert.*
import org.junit.Test

class ManualTransactionInputTest {
    private fun parse(amount: String = "1,000", account: String = "", balance: String = "") =
        manualTransactionInput(1000, TransactionType.DEPOSIT, amount, " 가상인물 ", account, balance)

    @Test fun validInputPreservesOptionalMeaning() {
        assertEquals(ManualTransactionInput(1000, TransactionType.DEPOSIT, 1000, "가상인물"), parse())
        assertEquals(ManualTransactionInput(1000, TransactionType.DEPOSIT, 1000, "가상인물", "0001", 0), parse(account = "0001", balance = "0"))
    }
    @Test fun invalidMoneyIsRejectedWithoutSilentCorrection() {
        for (text in listOf("", "0", "-1", "1.5", "1,00", "abc", "9223372036854775808")) {
            try { parse(amount = text); fail(text) } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun invalidBalanceIsRejected() {
        for (text in listOf("-1", "1,00", "abc", "9223372036854775808")) {
            try { parse(balance = text); fail(text) } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun invalidAccountIsRejected() {
        for (text in listOf("123", "12345", "abcd", "１２３４")) {
            try { parse(account = text); fail(text) } catch (_: IllegalArgumentException) { }
        }
    }
}
