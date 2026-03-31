package com.example.anonymousmeetup.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.anonymousmeetup.R
import javax.inject.Inject

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val message = inputData.getString("message") ?: return Result.failure()
        
        createNotificationChannel()
        showNotification(message)
        
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "РЎРѕРѕР±С‰РµРЅРёСЏ",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "РЈРІРµРґРѕРјР»РµРЅРёСЏ Рѕ РЅРѕРІС‹С… СЃРѕРѕР±С‰РµРЅРёСЏС…"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(message: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("РќРѕРІРѕРµ СЃРѕРѕР±С‰РµРЅРёРµ")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "messages_channel"
    }
}

