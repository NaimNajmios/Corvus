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
import com.najmi.corvus.domain.model.PipelineStep

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "fact_check_channel"
        private const val CHANNEL_NAME = "Fact Check Analysis"
        private const val CHANNEL_DESCRIPTION = "Displays progress and results for claim analysis"
        
        const val PROGRESS_NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getProgressNotificationBuilder(step: PipelineStep): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stepText = when (step) {
            PipelineStep.CHECKING_VIRAL_DATABASE -> "Checking viral database..."
            PipelineStep.CHECKING_KNOWN_FACTS -> "Checking known facts..."
            PipelineStep.DISSECTING -> "Dissecting claim..."
            PipelineStep.CHECKING_SUB_CLAIMS -> "Checking sub-claims..."
            PipelineStep.RETRIEVING_SOURCES -> "Retrieving sources..."
            PipelineStep.ANALYZING -> "Analyzing sources..."
            PipelineStep.DONE -> "Analysis complete"
            else -> "Processing..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Fallback to launcher icon
            .setContentTitle("Fact Checking in Progress")
            .setContentText(stepText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setProgress(0, 0, true)
    }

    fun showResultNotification(title: String, summary: String, resultId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("resultId", resultId)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        with(NotificationManagerCompat.from(context)) {
            // Check for permission in Android 13+ is required by system, 
            // but we assume it's granted for now as per plan focus on logic.
            notify(RESULT_NOTIFICATION_ID, notification)
        }
    }
}
