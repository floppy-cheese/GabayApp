package com.example.gabay.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.example.gabay.data.models.DisasterAlert
import com.example.gabay.data.models.DisasterType
import com.example.gabay.data.models.Severity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

data class TyphoonData(
    val name: String,
    val category: String,       // "Typhoon", "Tropical Storm", "Tropical Depression"
    val windSpeedKph: Double,
    val centerLat: Double,
    val centerLon: Double,
    val description: String,
    val timestamp: Long
)

class TyphoonRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("typhoon_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        // JTWC RSS feed — Western Pacific basin covers Philippine Area of Responsibility
        private const val JTWC_RSS = "https://www.metoc.navy.mil/jtwc/rss/western-pacific.xml"

        // Philippine Area of Responsibility bounding box
        private const val PAR_MIN_LAT = 3.0
        private const val PAR_MAX_LAT = 25.0
        private const val PAR_MIN_LON = 115.0
        private const val PAR_MAX_LON = 135.0
    }

    suspend fun getTyphoonAlerts(userLocation: Location?): List<DisasterAlert> {
        return withContext(Dispatchers.IO) {
            try {
                val xml = fetchRSS()
                val typhoons = parseJTWC(xml)
                cacheTyphooons(typhoons)
                buildAlerts(typhoons, userLocation)
            } catch (e: Exception) {
                android.util.Log.e("TyphoonRepo", "Fetch failed: ${e.message}")
                val cached = getCachedTyphoons()
                buildAlerts(cached, userLocation)
            }
        }
    }

    private fun fetchRSS(): String {
        val conn = URL(JTWC_RSS).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Gabay/1.0 Android Disaster App")
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun parseJTWC(xml: String): List<TyphoonData> {
        val results = mutableListOf<TyphoonData>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var inItem = false
            var title = ""
            var description = ""
            var pubDate = ""

            var eventType = parser.next()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "item" -> { inItem = true; title = ""; description = ""; pubDate = "" }
                        "title" -> if (inItem) title = parser.nextText()
                        "description" -> if (inItem) description = parser.nextText()
                        "pubdate" -> if (inItem) pubDate = parser.nextText()
                    }
                    XmlPullParser.END_TAG -> if (parser.name.lowercase() == "item" && inItem) {
                        inItem = false
                        parseTyphoonItem(title, description, pubDate)?.let { results.add(it) }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e("TyphoonRepo", "XML parse error: ${e.message}")
        }
        return results
    }

    private fun parseTyphoonItem(title: String, description: String, pubDate: String): TyphoonData? {
        // JTWC titles look like: "Tropical Storm DANIKA (WP022026)"
        // Description contains position, wind speed, etc.
        val titleLower = title.lowercase()

        val category = when {
            titleLower.contains("super typhoon") -> "Super Typhoon"
            titleLower.contains("typhoon") -> "Typhoon"
            titleLower.contains("tropical storm") -> "Tropical Storm"
            titleLower.contains("tropical depression") -> "Tropical Depression"
            else -> return null  // Skip non-cyclone items
        }

        // Extract storm name from title
        val nameRegex = Regex("""(?:super typhoon|typhoon|tropical storm|tropical depression)\s+([A-Z]+)""", RegexOption.IGNORE_CASE)
        val stormName = nameRegex.find(title)?.groupValues?.get(1) ?: "Unknown"

        // Extract position from description
        // JTWC format: "...CENTER LOCATED NEAR 14.2N 121.5E..."
        val posRegex = Regex("""(\d+\.\d+)([NS])\s+(\d+\.\d+)([EW])""")
        val posMatch = posRegex.find(description)

        val (centerLat, centerLon) = if (posMatch != null) {
            var lat = posMatch.groupValues[1].toDoubleOrNull() ?: return null
            var lon = posMatch.groupValues[3].toDoubleOrNull() ?: return null
            if (posMatch.groupValues[2] == "S") lat = -lat
            if (posMatch.groupValues[4] == "W") lon = -lon
            Pair(lat, lon)
        } else {
            // Try decimal format
            val decRegex = Regex("""LAT[:\s]+([-\d.]+).*LON[:\s]+([-\d.]+)""", RegexOption.IGNORE_CASE)
            val decMatch = decRegex.find(description) ?: return null
            Pair(
                decMatch.groupValues[1].toDoubleOrNull() ?: return null,
                decMatch.groupValues[2].toDoubleOrNull() ?: return null
            )
        }

        // Only include storms within or near PAR
        if (centerLat < PAR_MIN_LAT || centerLat > PAR_MAX_LAT ||
            centerLon < PAR_MIN_LON || centerLon > PAR_MAX_LON) {
            return null
        }

        // Extract max wind speed in knots, convert to kph
        val windRegex = Regex("""MAX(?:IMUM)?\s+(?:SUSTAINED\s+)?WINDS?\s*[:\s]+(\d+)\s*KT""", RegexOption.IGNORE_CASE)
        val windKnots = windRegex.find(description)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val windKph = windKnots * 1.852

        val timestamp = try {
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.ENGLISH)
                .parse(pubDate)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        return TyphoonData(
            name = stormName,
            category = category,
            windSpeedKph = windKph,
            centerLat = centerLat,
            centerLon = centerLon,
            description = "$category $stormName — Max winds ${windKph.toInt()} km/h",
            timestamp = timestamp
        )
    }

    private fun buildAlerts(typhoons: List<TyphoonData>, userLocation: Location?): List<DisasterAlert> {
        return typhoons.mapIndexed { index, typhoon ->
            val distanceKm = if (userLocation != null) {
                val typhoonLoc = Location("").apply {
                    latitude = typhoon.centerLat
                    longitude = typhoon.centerLon
                }
                userLocation.distanceTo(typhoonLoc) / 1000.0
            } else null

            val severity = calculateSeverity(typhoon.windSpeedKph, distanceKm ?: 999.0)

            // Map to PAGASA signal numbers for context
            val signalInfo = when {
                typhoon.windSpeedKph >= 185 -> "Signal No. 4 or higher"
                typhoon.windSpeedKph >= 118 -> "Signal No. 3"
                typhoon.windSpeedKph >= 89  -> "Signal No. 2"
                typhoon.windSpeedKph >= 62  -> "Signal No. 1"
                else                        -> "Under monitoring"
            }

            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
            val timeStr = "Updated: ${sdf.format(java.util.Date(typhoon.timestamp))}"

            DisasterAlert(
                id = "typhoon_$index",
                type = DisasterType.TYPHOON,
                description = "${typhoon.category} ${typhoon.name} — $signalInfo\n" +
                        "Max winds: ${typhoon.windSpeedKph.toInt()} km/h\n$timeStr",
                distanceKm = distanceKm ?: -1.0,
                severity = severity,
                isActive = true
            )
        }
    }

    private fun calculateSeverity(windKph: Double, distanceKm: Double): Severity {
        return when {
            windKph >= 185 && distanceKm <= 200 -> Severity.CRITICAL
            windKph >= 118 && distanceKm <= 200 -> Severity.HIGH
            windKph >= 89  && distanceKm <= 300 -> Severity.MODERATE
            windKph >= 62  && distanceKm <= 400 -> Severity.LOW
            else                                 -> Severity.LOW
        }
    }

    private fun cacheTyphooons(typhoons: List<TyphoonData>) {
        prefs.edit().putString("cached_typhoons", gson.toJson(typhoons)).apply()
    }

    private fun getCachedTyphoons(): List<TyphoonData> {
        val json = prefs.getString("cached_typhoons", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<TyphoonData>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { emptyList() }
    }
}