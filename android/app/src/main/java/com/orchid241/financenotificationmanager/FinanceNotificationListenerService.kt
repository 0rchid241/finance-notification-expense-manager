package com.orchid241.financenotificationmanager

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.orchid241.financenotificationmanager.data.RawNotificationRepository
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FinanceNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy {
        RawNotificationRepository(AppDatabase.getInstance(applicationContext).rawNotificationDao())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val receivedAt = System.currentTimeMillis()
        val notificationKey = sbn.key
        val packageName = sbn.packageName
        val postedAt = sbn.postTime
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        Log.d(
            TAG,
            "package=$packageName, title=$title, text=$text, postedAt=$postedAt",
        )

        serviceScope.launch {
            try {
                repository.saveRawNotification(
                    notificationKey = notificationKey,
                    packageName = packageName,
                    title = title,
                    text = text,
                    postedAt = postedAt,
                    receivedAt = receivedAt,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A failed insert must not cancel collection of subsequent events.
                Log.e(TAG, "Failed to save raw notification", error)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FinanceNotification"
    }
}
