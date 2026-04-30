package com.example.gabay.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.example.gabay.data.api.RetrofitInstance
import com.example.gabay.data.models.DisasterAlert
import com.example.gabay.data.models.DisasterType
import com.example.gabay.data.models.Severity
import com.google.gson.Gson

data class CachedEarthquake(
    val index: Int,
    val magnitude: Double,
    val place: String,
    val dateTimeStr: String,
    val epicenterLat: Double,
    val epicenterLon: Double,
    val timestampMs: Long
)

class DisasterRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("disaster_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val typhoonRepository = TyphoonRepository(context)

    companion object {
        const val RADIUS_KM = 200.0
        const val MIN_MAGNITUDE = 4.0
        const val MS_PER_DAY = 24 * 60 * 60 * 1000L
    }

    suspend fun getDisasterAlerts(userLocation: Location?): List<DisasterAlert> {
        // ── TEMPORARY TEST — remove before final build ──────────────────
        val mockTyphoon = DisasterAlert(
            id = "typhoon_test",
            type = DisasterType.TYPHOON,
            description = "Typhoon DANIKA — Signal No. 2\nMax winds: 120 km/h\nUpdated: Apr 29, 2026 at 06:00 PM",
            distanceKm = 150.0,
            severity = Severity.HIGH,
            isActive = true
        )
        return listOf(mockTyphoon)
        // ── END TEMPORARY TEST ──────────────────────────────────────────
        val earthquakeAlerts = try {
            val response = RetrofitInstance.earthquakeApi.getPhilippineEarthquakes()
            val rawEarthquakes = response.features.mapIndexedNotNull { index, feature ->
                val mag = feature.properties.magnitude ?: return@mapIndexedNotNull null
                if (mag < MIN_MAGNITUDE) return@mapIndexedNotNull null
                val coords = feature.geometry.coordinates
                if (coords.size < 2) return@mapIndexedNotNull null
                val rawPlace = feature.properties.place ?: "Unknown location"
                val place = parseUsgsPlace(rawPlace)
                val timestamp = feature.properties.time ?: return@mapIndexedNotNull null
                val sdf = java.text.SimpleDateFormat(
                    "MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault()
                )
                sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
                val dateTimeStr = "Reported on: ${sdf.format(java.util.Date(timestamp))}"
                CachedEarthquake(index, mag, place, dateTimeStr, coords[1], coords[0], timestamp)
            }
            cacheRawEarthquakes(rawEarthquakes)
            buildAlerts(rawEarthquakes, userLocation)
        } catch (e: Exception) {
            buildAlerts(getCachedEarthquakes(), userLocation)
        }

        val typhoonAlerts = try {
            typhoonRepository.getTyphoonAlerts(userLocation)
        } catch (e: Exception) {
            android.util.Log.e("DisasterRepo", "Typhoon fetch failed: ${e.message}")
            emptyList()
        }

        return earthquakeAlerts + typhoonAlerts


    }

    private fun buildAlerts(
        earthquakes: List<CachedEarthquake>,
        userLocation: Location?
    ): List<DisasterAlert> {
        val now = System.currentTimeMillis()

        return earthquakes.mapNotNull { eq ->
            val distanceKm = if (userLocation != null) {
                val eqLocation = Location("").apply {
                    latitude = eq.epicenterLat
                    longitude = eq.epicenterLon
                }
                userLocation.distanceTo(eqLocation) / 1000.0
            } else null

            // Filter by radius if location known
            if (distanceKm != null && distanceKm > RADIUS_KM) return@mapNotNull null

            // Time relevance filter
            val ageMs = now - eq.timestampMs
            val ageDays = ageMs / MS_PER_DAY
            val dist = distanceKm ?: 999.0

            val maxAgeDays = when {
                eq.magnitude >= 6.0 -> 7L          // Major quake always shown for 7 days
                dist <= 50.0        -> 7L           // Nearby: stay cautious for aftershocks
                dist <= 100.0       -> 3L           // Regional: 3 days
                else                -> 1L           // Distant minor: 1 day only
            }

            if (ageDays > maxAgeDays) return@mapNotNull null

            val severity = calculateSeverity(eq.magnitude, dist)

            // Aftershock warning for nearby older quakes
            val isOldNearby = ageDays >= 1 && dist <= 50.0
            val descriptionPrefix = if (isOldNearby) "⚠️ Aftershock risk — " else ""

            DisasterAlert(
                id = "eq_${eq.index}",
                type = DisasterType.EARTHQUAKE,
                description = "${descriptionPrefix}Magnitude ${String.format("%.1f", eq.magnitude)} — ${eq.place}\n${eq.dateTimeStr}",
                distanceKm = distanceKm ?: -1.0,
                severity = severity,
                isActive = true
            )
        }
    }

    private fun parseUsgsPlace(raw: String): String {
        val regex = Regex("""^\d+(\.\d+)?\s+km\s+\w+\s+of\s+(.+),\s*Philippines$""")
        val match = regex.find(raw.trim())

        return if (match != null) {
            val locationName = match.groupValues[2].trim()
            val province = lookupProvince(locationName)
            if (province != null) "$locationName, $province"
            else locationName
        } else {
            raw.replace(Regex("""^\d+(\.\d+)?\s+km\s+\w+\s+of\s+"""), "")
                .replace(", Philippines", "")
                .replace("Philippines", "")
                .trim()
        }
    }

    private fun lookupProvince(locationName: String): String? {
        val lookup = mapOf(
            "Hukay" to "Calatagan, Batangas",
            "Calatagan" to "Batangas",
            "Lipa" to "Batangas",
            "Batangas" to "Batangas",
            "Nasugbu" to "Batangas",
            "Taal" to "Batangas",
            "Bacoor" to "Cavite",
            "Tagaytay" to "Cavite",
            "Cavite" to "Cavite",
            "Manila" to "Metro Manila",
            "Makati" to "Metro Manila",
            "Quezon City" to "Metro Manila",
            "Pasig" to "Metro Manila",
            "Valenzuela" to "Metro Manila",
            "Caloocan" to "Metro Manila",
            "Malabon" to "Metro Manila",
            "Navotas" to "Metro Manila",
            "Marikina" to "Metro Manila",
            "Mandaluyong" to "Metro Manila",
            "Taguig" to "Metro Manila",
            "Paranaque" to "Metro Manila",
            "Cebu" to "Cebu",
            "Bohol" to "Bohol",
            "Leyte" to "Leyte",
            "Tacloban" to "Leyte",
            "Ormoc" to "Leyte",
            "Biri" to "Northern Samar",
            "Can-Avid" to "Eastern Samar",
            "Borongan" to "Eastern Samar",
            "Manay" to "Davao Oriental",
            "Davao" to "Davao del Sur",
            "Cotabato" to "North Cotabato",
            "Mati" to "Davao Oriental",
            "Surigao" to "Surigao del Norte",
            "Butuan" to "Agusan del Norte",
            "Cagayan de Oro" to "Misamis Oriental",
            "Iligan" to "Lanao del Norte",
            "Zamboanga" to "Zamboanga del Sur",
            "General Santos" to "South Cotabato",
            "Sarangani" to "Sarangani",
            "Baguio" to "Benguet",
            "Pangasinan" to "Pangasinan",
            "Dagupan" to "Pangasinan",
            "Olongapo" to "Zambales",
            "Angeles" to "Pampanga",
            "Pampanga" to "Pampanga",
            "Tarlac" to "Tarlac",
            "Cabanatuan" to "Nueva Ecija",
            "Palayan" to "Nueva Ecija",
            "Tuguegarao" to "Cagayan",
            "Ilagan" to "Isabela",
            "Naga" to "Camarines Sur",
            "Legazpi" to "Albay",
            "Sorsogon" to "Sorsogon",
            "Bontoc" to "Mountain Province",
            "Tabuk" to "Kalinga",
            "Vigan" to "Ilocos Sur",
            "Laoag" to "Ilocos Norte",
            "San Fernando" to "La Union",
            "Iloilo" to "Iloilo",
            "Bacolod" to "Negros Occidental",
            "Dumaguete" to "Negros Oriental",
            "Roxas" to "Capiz",
            "Puerto Princesa" to "Palawan",
            "Palawan" to "Palawan",
            "Tingloy" to "Batangas"
        )

        lookup[locationName]?.let { return it }
        for ((key, value) in lookup) {
            if (locationName.contains(key, ignoreCase = true) ||
                key.contains(locationName, ignoreCase = true)) {
                return value
            }
        }
        return null
    }

    private fun calculateSeverity(mag: Double, distanceKm: Double): Severity {
        return when {
            mag >= 6.0 && distanceKm <= 50.0  -> Severity.CRITICAL
            mag >= 6.0 && distanceKm <= 100.0 -> Severity.CRITICAL
            mag >= 6.0                         -> Severity.HIGH
            mag >= 5.0 && distanceKm <= 50.0  -> Severity.HIGH
            mag >= 5.0 && distanceKm <= 100.0 -> Severity.MODERATE
            mag >= 4.0 && distanceKm <= 50.0  -> Severity.MODERATE
            else                               -> Severity.LOW
        }
    }

    private fun cacheRawEarthquakes(earthquakes: List<CachedEarthquake>) {
        val json = gson.toJson(earthquakes)
        prefs.edit().putString("cached_earthquakes", json).apply()
    }

    private fun getCachedEarthquakes(): List<CachedEarthquake> {
        val json = prefs.getString("cached_earthquakes", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<CachedEarthquake>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCachedAlerts(): List<DisasterAlert> = emptyList()

    suspend fun getCachedEarthquakesAsAlerts(userLocation: Location?): List<DisasterAlert> {
        val cached = getCachedEarthquakes()
        return buildAlerts(cached, userLocation)
    }

    init {
        // Clear old cache format to prevent stale alerts from showing
        prefs.edit()
            .remove("cached_alerts")     // old format
            .remove("cached_earthquakes") // current format — force fresh fetch
            .apply()
    }
}