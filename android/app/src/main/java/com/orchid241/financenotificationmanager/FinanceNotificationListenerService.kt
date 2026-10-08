package com.orchid241.financenotificationmanager

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.parser.ParseResult
import com.orchid241.financenotificationmanager.parser.SupportedFinancialApps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FinanceNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy {
        FinancialTransactionRepository(AppDatabase.getInstance(applicationContext))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 샘플 수집 단계에서 실제 설치 앱의 packageName을 확인하기 위한 임시 로그.
        // 알림 제목/본문 등 금융 내용은 로그에 남기지 않는다.
        Log.d(DISCOVERY_TAG, "package=${sbn.packageName}")

        if (!SupportedFinancialApps.supports(sbn.packageName)) return
        val receivedAt = System.currentTimeMillis()
        val notificationKey = sbn.key
        val packageName = sbn.packageName
        val postedAt = sbn.postTime
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        serviceScope.launch {
            try {
                val result = repository.collectNotification(
                    notificationKey = notificationKey,
                    packageName = packageName,
                    title = title,
                    text = text,
                    postedAt = postedAt,
                    receivedAt = receivedAt,
                )
                Log.d(TAG, "package=$packageName, saved=true, parsed=${result is ParseResult.Success}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed insert must not cancel collection of subsequent events.
                // Exception messages can contain SQL values; never log financial content.
                Log.e(TAG, "package=$packageName, processingSaved=false")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FinanceNotification"
        const val DISCOVERY_TAG = "FinanceAppDiscovery"
    }
}
