package com.orchid241.financenotificationmanager.mockwallet

import androidx.room.withTransaction
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.RawProcessingStatus
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 별도 테스트 DB만 받는다. Raw 콜백은 모두 보존하고 거래 저장은 기존 Repository에 위임한다. */
class SyntheticWalletProcessor(private val database: AppDatabase, private val cache: WalletEventCache = WalletEventCache()) {
    private val parser = SyntheticWalletParser()
    private val repository = FinancialTransactionRepository(database)
    private val mutex = Mutex()

    suspend fun receive(input: RawNotificationEntity): String = mutex.withLock {
        require(input.packageName == SyntheticWalletParser.PACKAGE)
        val dao = database.rawNotificationDao()
        val id = dao.insert(input.copy(id = 0, processingStatus = "PENDING"))
        val raw = input.copy(id = id)
        try {
            database.withTransaction {
                val fragment = parser.fragment(raw)
                val status = if (fragment == null) "MOCK_INVALID" else {
                    // 메모리 퇴출/앱·서비스 재시작/사용자 거래 삭제 뒤에도 처리 영수증은 Raw에 남는다.
                    val processed = dao.getAll().any { it.id != id && it.processingStatus == RawProcessingStatus.PROCESSED && parser.fragment(it)?.identity == fragment.identity }
                    if (processed) "MOCK_DUPLICATE" else when (val result = cache.offer(fragment, raw.receivedAt)) {
                        is WalletEventCache.Result.Ready -> {
                            repository.processStoredNotification(id, result.fragment.toResult(id))
                            RawProcessingStatus.PROCESSED
                        }
                        WalletEventCache.Result.Waiting -> "MOCK_WAITING"
                        WalletEventCache.Result.Duplicate -> "MOCK_DUPLICATE"
                        WalletEventCache.Result.Conflict -> "MOCK_CONFLICT"
                    }
                }
                dao.updateProcessingStatus(id, status)
                status
            }
        } catch (failure: Exception) {
            // DB 저장 실패 시 메모리의 완료 표시로 재시도를 잃지 않는다.
            cache.clear()
            throw failure
        }
    }
}
