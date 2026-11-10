package com.chatverse.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ChatVerseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenManager.cacheToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "ChatVerse"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChatVerse messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Chat and community notifications" }
            manager.createNotificationChannel(channel)
        }

        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.take(80))
            .setContentText(body.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(500)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "chatverse_messages"
    }
}
