package com.najmi.corvus.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.najmi.corvus.MainActivity
import com.najmi.corvus.R
import com.najmi.corvus.domain.model.CheckingStatus

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "fact_check_channel"
        private const val CHANNEL_NAME = "Fact Check Analysis"
        private const val CHANNEL_DESCRIPTION = "Displays progress for claim analysis"

        const val RESULT_CHANNEL_ID = "fact_check_result_channel"
        private const val RESULT_CHANNEL_NAME = "Fact Check Results"
        private const val RESULT_CHANNEL_DESCRIPTION = "Displays completed fact check results"
        
        const val PROGRESS_NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002

        const val EXTRA_RESULT_ID = "resultId"
        const val EXTRA_SHARED_TEXT = "sharedText"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(progressChannel)

            val resultChannel = NotificationChannel(RESULT_CHANNEL_ID, RESULT_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = RESULT_CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(resultChannel)
        }
    }

    fun getProgressNotificationBuilder(status: CheckingStatus): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Fact Checking in Progress")
            .setContentText(status.status)
            .setSubText("Tap to return to app")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .setProgress(100, status.progress, false)
    }

    fun showProgressNotification(status: CheckingStatus) {
        val notification = getProgressNotificationBuilder(status).build()
        with(NotificationManagerCompat.from(context)) {
            notify(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    fun showResultNotification(title: String, summary: String, resultId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_RESULT_ID, resultId)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(RESULT_NOTIFICATION_ID, notification)
        }
    }

    fun cancelProgressNotification() {
        with(NotificationManagerCompat.from(context)) {
            cancel(PROGRESS_NOTIFICATION_ID)
        }
    }
}
