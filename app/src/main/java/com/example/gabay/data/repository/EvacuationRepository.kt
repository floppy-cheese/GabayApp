package com.example.gabay.data.repository

import android.content.Context
import com.example.gabay.data.models.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.*

class EvacuationRepository(private val context: Context) {

    private var allCenters: List<EvacuationCenter> = emptyList()
    private val gson = Gson()
    private var isLoaded = false

    // No longer called in init — called lazily on IO thread
    private suspend fun ensureLoaded() {
        if (isLoaded) return
        withContext(Dispatchers.IO) {
            loadEvacuationCenters()
            isLoaded = true
        }
    }

    private fun loadEvacuationCenters() {
        try {
            // Read entire file at once — no line-by-line fragility
            val json = context.assets.open("ph_evacs_merged.geojson")
                .bufferedReader()
                .readText()

            val root = gson.fromJson(json, JsonObject::class.java)
            val featuresArray = root.getAsJsonArray("features")

            android.util.Log.d("EvacuationRepo", "Parsed ${featuresArray.size()} features from GeoJSON")

            allCenters = featuresArray.mapIndexedNotNull { index, element ->
                try {
                    val feature = element.asJsonObject
                    val properties = feature.getAsJsonObject("properties")
                    val geometry = feature.getAsJsonObject("geometry")
                    val geometryType = geometry.get("type").asString

                    val centerPoint = when (geometryType) {
                        "Polygon" -> {
                            val coordinates = geometry.getAsJsonArray("coordinates")
                            val outerRing = coordinates[0].asJsonArray
                            calculatePolygonCentroid(outerRing)
                        }
                        "Point" -> {
                            val coordinates = geometry.getAsJsonArray("coordinates")
                            Pair(coordinates[0].asDouble, coordinates[1].asDouble)
                        }
                        else -> null
                    } ?: return@mapIndexedNotNull null

                    val longitude = centerPoint.first
                    val latitude = centerPoint.second

                    val name = properties.get("name")?.asString ?: "Evacuation Center"

                    val place = properties.get("place")?.takeIf { !it.isJsonNull }?.asString
                    val city = properties.get("city")?.takeIf { !it.isJsonNull }?.asString
                    val municipality = properties.get("municipality")?.takeIf { !it.isJsonNull }?.asString
                    val province = properties.get("province")?.asString ?: "Unknown Province"

                    val addressParts = mutableListOf<String>()
                    place?.let { addressParts.add(it) }
                    city?.let { addressParts.add(it) }
                    municipality?.let { addressParts.add(it) }
                    addressParts.add(province)
                    val address = addressParts.joinToString(", ")

                    val capacity = properties.get("capacity")?.takeIf { !it.isJsonNull }?.asInt ?: 1000
                    val type = properties.get("type")?.asString ?: "shelter"

                    EvacuationCenter(
                        id = "evac_$index",
                        name = name,
                        address = address,
                        latitude = latitude,
                        longitude = longitude,
                        capacity = capacity,
                        contactNumber = null,
                        facilities = determineFacilities(type)
                    )
                } catch (e: Exception) {
                    android.util.Log.e("EvacuationRepo", "Error parsing feature $index: ${e.message}")
                    null
                }
            }

            android.util.Log.d("EvacuationRepo", "Successfully loaded ${allCenters.size} evacuation centers")

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("EvacuationRepo", "Error loading evacuation centers: ${e.message}")
            allCenters = emptyList()
        }
    }

    private fun calculatePolygonCentroid(coordinates: JsonArray): Pair<Double, Double> {
        var sumLon = 0.0
        var sumLat = 0.0
        var count = 0

        for (i in 0 until coordinates.size()) {
            val point = coordinates[i].asJsonArray
            sumLon += point[0].asDouble
            sumLat += point[1].asDouble
            count++
        }

        return Pair(sumLon / count, sumLat / count)
    }

    private fun determineFacilities(type: String?): List<String> {
        return when (type?.lowercase()) {
            "campus", "school", "elementary school", "high school" ->
                listOf("Classrooms", "Toilets", "Water", "Covered Area")
            "gymnasium", "gym", "sports complex" ->
                listOf("Indoor Space", "Toilets", "Water", "First Aid")
            "covered court", "basketball court", "multi-purpose hall" ->
                listOf("Covered Area", "Open Space", "Toilets")
            "barangay hall", "municipal hall", "city hall" ->
                listOf("Office Space", "Toilets", "Water", "Security")
            "church", "chapel" ->
                listOf("Indoor Space", "Toilets", "Seating Area")
            else ->
                listOf("Shelter", "Water", "Toilets")
        }
    }

    suspend fun findNearestCenter(userLat: Double, userLon: Double): EvacuationCenter? {
        ensureLoaded()
        if (allCenters.isEmpty()) {
            android.util.Log.e("EvacuationRepo", "No evacuation centers loaded!")
            return null
        }
        return allCenters
            .map { center ->
                val distance = calculateDistance(userLat, userLon, center.latitude, center.longitude)
                Pair(center, distance)
            }
            .minByOrNull { it.second }
            ?.first
    }

    suspend fun getAllCenters(): List<EvacuationCenter> {
        ensureLoaded()
        return allCenters
    }

    suspend fun calculateRouteWithOSRM(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        mode: TravelMode
    ): RouteInfo {
        // Calculate with simple realistic speeds
        val distanceKm = calculateDistance(fromLat, fromLon, toLat, toLon)

        val timeMinutes = if (mode == TravelMode.WALKING) {
            // Walking: 4 km/h average speed
            ((distanceKm / 4.0) * 60).toInt()
        } else {
            // Vehicle: 25 km/h in city/emergency conditions
            ((distanceKm / 25.0) * 60).toInt()
        }

        android.util.Log.d("EvacuationRepo", "=== ROUTE CALCULATION ===")
        android.util.Log.d("EvacuationRepo", "Distance: ${distanceKm}km")
        android.util.Log.d("EvacuationRepo", "Travel mode: $mode")
        android.util.Log.d("EvacuationRepo", "Speed: ${if (mode == TravelMode.WALKING) "4 km/h" else "25 km/h"}")
        android.util.Log.d("EvacuationRepo", "Calculated time: $timeMinutes minutes")

        // Try to get real route from Valhalla
        return try {
            val costing = if (mode == TravelMode.WALKING) "pedestrian" else "auto"

            // Valhalla API endpoint (using public instance)
            val requestBody = if (mode == TravelMode.WALKING) {
                """
                {
                    "locations": [
                        {"lat": $fromLat, "lon": $fromLon, "radius": 100},
                        {"lat": $toLat, "lon": $toLon, "radius": 100}
                    ],
                    "costing": "pedestrian",
                    "costing_options": {
                        "pedestrian": {
                            "use_roads": 1.0,
                            "use_hills": 0.5,
                            "walkway_factor": 1.0,
                            "alley_factor": 2.0,
                            "driveway_factor": 2.0,
                            "step_penalty": 30
                        }
                    },
                    "directions_options": {"units": "kilometers"}
                }
                """.trimIndent()
                    } else {
                        """
                {
                    "locations": [
                        {"lat": $fromLat, "lon": $fromLon, "radius": 100},
                        {"lat": $toLat, "lon": $toLon, "radius": 100}
                    ],
                    "costing": "auto",
                    "directions_options": {"units": "kilometers"}
                }
                """.trimIndent()
                    }

            val connection = java.net.URL("https://valhalla1.openstreetmap.de/route")
                .openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            val responseCode = connection.responseCode
            android.util.Log.d("EvacuationRepo", "Valhalla Response Code: $responseCode")

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = gson.fromJson(response, JsonObject::class.java)

                val trip = jsonResponse.getAsJsonObject("trip")
                val legs = trip.getAsJsonArray("legs")

                if (legs.size() > 0) {
                    val leg = legs[0].asJsonObject
                    val summary = leg.getAsJsonObject("summary")

                    val lengthKm = summary.get("length").asDouble
                    val timeSeconds = summary.get("time").asDouble
                    val valhallaTimeMinutes = (timeSeconds / 60).toInt()

                    android.util.Log.d("EvacuationRepo", "Valhalla distance: ${lengthKm}km")
                    android.util.Log.d("EvacuationRepo", "Valhalla time: ${valhallaTimeMinutes}min")

                    // Extract route points
                    val shape = leg.get("shape").asString
                    val waypoints = decodePolyline(shape)

                    android.util.Log.d("EvacuationRepo", "Using Valhalla route with ${waypoints.size} points")

                    return RouteInfo(lengthKm, valhallaTimeMinutes, mode, waypoints)
                }
            }

            android.util.Log.w("EvacuationRepo", "Valhalla failed, using calculated route")
            createCalculatedRoute(fromLat, fromLon, toLat, toLon, distanceKm, timeMinutes, mode)

        } catch (e: Exception) {
            android.util.Log.e("EvacuationRepo", "Valhalla error: ${e.message}")
            createCalculatedRoute(fromLat, fromLon, toLat, toLon, distanceKm, timeMinutes, mode)
        }
    }

    private fun createCalculatedRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        distanceKm: Double,
        timeMinutes: Int,
        mode: TravelMode
    ): RouteInfo {
        // Create smooth intermediate points
        val waypoints = mutableListOf<Waypoint>()
        waypoints.add(Waypoint(fromLat, fromLon, "Start"))

        for (i in 1..5) {
            val fraction = i / 6.0
            val lat = fromLat + (toLat - fromLat) * fraction
            val lon = fromLon + (toLon - fromLon) * fraction
            waypoints.add(Waypoint(lat, lon, "Via point $i"))
        }

        waypoints.add(Waypoint(toLat, toLon, "Destination"))

        return RouteInfo(distanceKm, timeMinutes, mode, waypoints)
    }

    private fun decodePolyline(encoded: String): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()
        var index = 0
        var lat = 0
        var lon = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlon = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lon += dlon

            waypoints.add(Waypoint(lat / 1e6, lon / 1e6, "Route point"))
        }

        return waypoints
    }

    fun calculateRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        mode: TravelMode
    ): RouteInfo {
        val distanceKm = calculateDistance(fromLat, fromLon, toLat, toLon)
        val timeMinutes = if (mode == TravelMode.WALKING) {
            ((distanceKm / 4.0) * 60).toInt()
        } else {
            ((distanceKm / 25.0) * 60).toInt()
        }

        return createCalculatedRoute(fromLat, fromLon, toLat, toLon, distanceKm, timeMinutes, mode)
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}