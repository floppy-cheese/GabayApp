package com.example.gabay.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.gabay.MainActivity
import com.example.gabay.R
import com.example.gabay.data.models.DisasterAlert
import com.example.gabay.data.models.Severity
import com.example.gabay.ui.home.FullScreenAlertActivity

object AlertNotificationManager {

    private const val CHANNEL_ID_CRITICAL = "gabay_critical_alerts"
    private const val CHANNEL_ID_GENERAL = "gabay_general_alerts"
    private const val NOTIFICATION_ID = 1001

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Critical channel — high priority, makes sound, shows heads-up
        val criticalChannel = NotificationChannel(
            CHANNEL_ID_CRITICAL,
            "Critical Disaster Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent evacuation and critical disaster warnings"
            enableVibration(true)
            enableLights(true)
        }

        // General channel — lower priority for informational alerts
        val generalChannel = NotificationChannel(
            CHANNEL_ID_GENERAL,
            "Disaster Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General disaster activity in your area"
        }

        notificationManager.createNotificationChannel(criticalChannel)
        notificationManager.createNotificationChannel(generalChannel)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendDisasterNotification(context: Context, alert: DisasterAlert) {
        val isCritical = alert.severity == Severity.CRITICAL
        val isHighOrCritical = alert.severity == Severity.HIGH || isCritical

        // M5.0+ → full-screen alert activity
        if (isHighOrCritical) {
            val fullScreenIntent = Intent(context, FullScreenAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("magnitude", extractMagnitude(alert.description))
                putExtra("location", extractLocation(alert.description))
                putExtra("distance", alert.distanceKm)
                putExtra("reportedTime", extractTime(alert.description))
                putExtra("actionText", if (isCritical) "EVACUATE NOW" else "PREPARE TO EVACUATE")
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 1, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val (title, message) = buildNotificationContent(alert)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_CRITICAL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) { }

        } else {
            // M4.5+ → standard notification, respects DND
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val (title, message) = buildNotificationContent(alert)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) { }
        }
    }

    // Helper functions to extract parts from description string
    private fun extractMagnitude(description: String): Double {
        val regex = Regex("""Magnitude (\d+\.\d+)""")
        return regex.find(description)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun extractLocation(description: String): String {
        return description.lines().firstOrNull()
            ?.replace(Regex("""^Magnitude \d+\.\d+ — """), "")
            ?: "Unknown location"
    }

    private fun extractTime(description: String): String {
        return description.lines().getOrNull(1) ?: ""
    }

    private fun buildNotificationContent(alert: DisasterAlert): Pair<String, String> {
        val distanceText = "~${String.format("%.1f", alert.distanceKm)} km from you"
        return when (alert.severity) {
            Severity.CRITICAL -> Pair(
                "🚨 EVACUATE NOW",
                "${alert.description} | $distanceText. Move to your nearest evacuation center immediately."
            )
            Severity.HIGH -> Pair(
                "⚠️ Earthquake Warning",
                "${alert.description} | $distanceText. Prepare to evacuate. Stay alert."
            )
            Severity.MODERATE -> Pair(
                "⚠️ Earthquake Detected",
                "${alert.description} | $distanceText. Monitor the situation."
            )
            Severity.LOW -> Pair(
                "ℹ️ Seismic Activity",
                "${alert.description} | $distanceText."
            )
        }
    }
}