package com.example.anonymousmeetup.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.anonymousmeetup.R

class LocationService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(TRACKING_NOTIFICATION_ID, buildTrackingNotification())
        // In anonymous mode we do not upload shared locations to server.
        return START_STICKY
    }

    private fun buildTrackingNotification(): Notification {
        val channelId = TRACKING_CHANNEL_ID
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Локальная геолокация",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Локальный режим геолокации без публикации идентификаторов"
            }
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Геолокация")
            .setContentText("Локальный режим, без серверного трекинга")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TRACKING_CHANNEL_ID = "tracking_channel"
        private const val TRACKING_NOTIFICATION_ID = 1001
    }
}
