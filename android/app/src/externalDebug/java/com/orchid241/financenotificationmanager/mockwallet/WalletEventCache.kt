package com.orchid241.financenotificationmanager.mockwallet

/** 순수 Kotlin. TTL은 최초 수신 기준이며 반복 콜백으로 늘리지 않는다. */
class WalletEventCache(private val ttlMillis: Long = 5_000, private val maxEntries: Int = 64) {
    init { require(ttlMillis > 0 && maxEntries > 0) }
    private data class Entry(val firstSeen: Long, val fragment: WalletFragment, val conflict: Boolean = false, val complete: Boolean = false)
    private val entries = linkedMapOf<WalletEventId, Entry>()
    val size: Int get() = entries.size
    fun clear() = entries.clear()

    sealed interface Result {
        data class Ready(val fragment: WalletFragment) : Result
        data object Waiting : Result
        data object Duplicate : Result
        data object Conflict : Result
    }

    fun offer(fragment: WalletFragment, now: Long): Result {
        entries.entries.removeAll { now < it.value.firstSeen || now - it.value.firstSeen >= ttlMillis }
        val old = entries[fragment.identity]
        if (old?.conflict == true) return Result.Conflict
        fun <T> conflicts(a: T?, b: T?) = a != null && b != null && a != b
        if (old != null && (conflicts(old.fragment.amount, fragment.amount) || conflicts(old.fragment.merchant, fragment.merchant) ||
                conflicts(old.fragment.account, fragment.account) || conflicts(old.fragment.balance, fragment.balance))) {
            entries[fragment.identity] = old.copy(conflict = true)
            return Result.Conflict
        }
        if (old?.complete == true) return Result.Duplicate
        val merged = fragment.copy(amount = fragment.amount ?: old?.fragment?.amount, merchant = fragment.merchant ?: old?.fragment?.merchant,
            account = fragment.account ?: old?.fragment?.account, balance = fragment.balance ?: old?.fragment?.balance)
        if (old == null && entries.size >= maxEntries) entries.remove(entries.keys.first())
        val complete = merged.amount != null && merged.merchant != null
        entries[fragment.identity] = Entry(old?.firstSeen ?: now, merged, complete = complete)
        return if (complete) Result.Ready(merged) else Result.Waiting
    }
}
