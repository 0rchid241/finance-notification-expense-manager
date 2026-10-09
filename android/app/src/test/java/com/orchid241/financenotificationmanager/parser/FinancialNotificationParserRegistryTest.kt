package com.orchid241.financenotificationmanager.parser

import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialNotificationParserRegistryTest {
    private fun raw(packageName: String) = RawNotificationEntity(
        id = 1,
        notificationKey = "key",
        packageName = packageName,
        title = "title",
        text = "text",
        postedAt = 1000,
        receivedAt = 2000,
    )

    @Test fun selectsParserByPackageName() {
        val expected = ParseResult.Success(
            ParsedFinancialTransaction(
                rawNotificationId = 1,
                transactionType = TransactionType.DEPOSIT,
                amount = 1000,
                counterparty = "가상상점",
                accountLast4 = "1234",
                balance = 5000,
                source = "테스트",
                occurredAt = 1000,
            ),
        )
        val parser = object : FinancialNotificationParser {
            override fun supports(packageName: String) = packageName == "com.example.bank"
            override fun parse(raw: RawNotificationEntity): ParseResult = expected
        }
        val registry = FinancialNotificationParserRegistry(listOf(parser))

        assertTrue(registry.supportsPackage("com.example.bank"))
        assertEquals(expected, registry.parse(raw("com.example.bank")))
    }

    @Test fun unsupportedPackageFailsWithoutParsing() {
        var called = false
        val parser = object : FinancialNotificationParser {
            override fun supports(packageName: String) = packageName == "com.example.bank"
            override fun parse(raw: RawNotificationEntity): ParseResult {
                called = true
                return ParseResult.Failure
            }
        }
        val registry = FinancialNotificationParserRegistry(listOf(parser))

        assertFalse(registry.supportsPackage("com.example.other"))
        assertEquals(ParseResult.Failure, registry.parse(raw("com.example.other")))
        assertFalse(called)
    }
}
