package com.orchid241.financenotificationmanager.mockwallet

import com.orchid241.financenotificationmanager.parser.TransactionType
import org.junit.Assert.*
import org.junit.Test

class WalletEventCacheTest {
    private fun part(event: String = "a", amount: Long? = 1000, merchant: String? = null) =
        WalletFragment(WalletEventId(SyntheticWalletParser.PACKAGE, "key", event, 1000, TransactionType.WITHDRAWAL), amount, merchant, null, null)
    @Test fun mergesOnlyComplementaryUpdatesInEitherOrder() {
        for (reverse in listOf(false,true)) {
            val cache = WalletEventCache()
            val parts = listOf(part(), part(amount = null, merchant = "가상")).let { if(reverse) it.reversed() else it }
            assertEquals(WalletEventCache.Result.Waiting, cache.offer(parts[0], 1000))
            val merged = (cache.offer(parts[1], 1100) as WalletEventCache.Result.Ready).fragment
            assertEquals(1000L, merged.amount); assertEquals("가상", merged.merchant)
        }
    }
    @Test fun doesNotMergeDifferentEventIdsOrPackagesKeysTimes() {
        val identities = listOf(part("b"), part().let { it.copy(identity=it.identity.copy(notificationKey="other")) },
            part().let { it.copy(identity=it.identity.copy(packageName="other")) }, part().let { it.copy(identity=it.identity.copy(occurredAt=2000)) })
        identities.forEach { other ->
            val cache = WalletEventCache(); cache.offer(part(),1000)
            assertEquals(WalletEventCache.Result.Waiting, cache.offer(other.copy(amount=null,merchant="가상"),1001))
        }
    }
    @Test fun ttlBoundaryExpiresWithoutSlidingOnDuplicateUpdates() {
        val cache=WalletEventCache(100,10)
        cache.offer(part(),1000); cache.offer(part(),1099)
        assertEquals(WalletEventCache.Result.Waiting,cache.offer(part(amount=null,merchant="가상"),1100))
    }
    @Test fun sameAmountDistinctPaymentsBothComplete() {
        val cache=WalletEventCache()
        assertTrue(cache.offer(part(merchant="가상"),1000) is WalletEventCache.Result.Ready)
        assertTrue(cache.offer(part("b",merchant="가상"),1001) is WalletEventCache.Result.Ready)
    }
    @Test fun duplicatesAreSuppressedButConflictsNeverOverwrite() {
        val cache=WalletEventCache()
        cache.offer(part(merchant="가상"),1000)
        assertEquals(WalletEventCache.Result.Duplicate,cache.offer(part(merchant="가상"),1001))
        assertEquals(WalletEventCache.Result.Conflict,cache.offer(part(amount=2000,merchant="가상"),1002))
    }
    @Test fun conflictBlocksRemainingFragmentsUntilExpiry() {
        val cache=WalletEventCache(100,10)
        cache.offer(part(),1000)
        assertEquals(WalletEventCache.Result.Conflict,cache.offer(part(amount=2000),1001))
        assertEquals(WalletEventCache.Result.Conflict,cache.offer(part(amount=null,merchant="가상"),1002))
        assertTrue(cache.offer(part(merchant="가상"),1100) is WalletEventCache.Result.Ready)
    }
    @Test fun memoryBoundEvictsOldestPartialWithoutInventingFields() {
        val cache=WalletEventCache(maxEntries=2)
        cache.offer(part(),1000); cache.offer(part("b"),1001); cache.offer(part("c"),1002)
        assertEquals(2,cache.size)
        assertEquals(WalletEventCache.Result.Waiting,cache.offer(part(amount=null,merchant="가상"),1003))
    }
    @Test fun clockRollbackDropsOldFragmentsSafely() {
        val cache=WalletEventCache(); cache.offer(part(),1000)
        assertEquals(WalletEventCache.Result.Waiting,cache.offer(part(amount=null,merchant="가상"),999))
    }
    @Test fun restartStartsEmptyAndInvalidConfigurationFails() {
        val first=WalletEventCache(); first.offer(part(),1000); first.clear(); assertEquals(0,first.size)
        assertEquals(WalletEventCache.Result.Waiting,WalletEventCache().offer(part(amount=null,merchant="가상"),1001))
        assertThrows(IllegalArgumentException::class.java) { WalletEventCache(0,1) }
        assertThrows(IllegalArgumentException::class.java) { WalletEventCache(1,0) }
    }
}
