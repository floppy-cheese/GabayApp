package com.example.gabay.worker

import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gabay.data.models.Severity
import com.example.gabay.data.repository.DisasterRepository
import com.example.gabay.util.AlertNotificationManager

class DisasterCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val disasterRepo = DisasterRepository(context)

            // Get last known location from cache
            val locationPrefs = context.getSharedPreferences("location_cache", Context.MODE_PRIVATE)
            val lat = locationPrefs.getFloat("last_lat", 0f).toDouble()
            val lon = locationPrefs.getFloat("last_lon", 0f).toDouble()

            val location = if (lat != 0.0 && lon != 0.0) {
                Location("").apply {
                    latitude = lat
                    longitude = lon
                }
            } else null

            val alerts = disasterRepo.getDisasterAlerts(location)

            // Only notify for HIGH and CRITICAL
            val notifiableAlerts = alerts.filter {
                it.severity == Severity.HIGH || it.severity == Severity.CRITICAL
            }

            if (notifiableAlerts.isNotEmpty()) {
                val mostSevere = notifiableAlerts.maxByOrNull { it.severity.ordinal }!!
                AlertNotificationManager.sendDisasterNotification(context, mostSevere)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}