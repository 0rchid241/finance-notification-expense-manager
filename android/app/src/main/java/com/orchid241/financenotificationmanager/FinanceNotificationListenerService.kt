package com.orchid241.financenotificationmanager

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FinanceNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()

        val text = extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()

        Log.d(
            "FinanceNotification",
            "package=${sbn.packageName}, " +
                    "title=$title, " +
                    "text=$text, " +
                    "postedAt=${sbn.postTime}"
        )
    }
}