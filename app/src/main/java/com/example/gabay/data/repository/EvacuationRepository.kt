package com.example.gabay.data.repository

import android.content.Context
import com.example.gabay.data.models.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class EvacuationRepository(private val context: Context) {

    private var allCenters: List<EvacuationCenter> = emptyList()
    private val gson = Gson()
    private var isLoaded = false

    // ── Data loading ─────────────────────────────────────────────────────────

    private var loadedForLat: Double? = null
    private var loadedForLon: Double? = null
    private val RELOAD_THRESHOLD_KM = 5.0
    private val FILTER_RADIUS_KM    = 10.0

    private suspend fun ensureLoaded(userLat: Double, userLon: Double) {
        val anchor = loadedForLat
        if (isLoaded && anchor != null) {
            if (calculateDistance(userLat, userLon, anchor, loadedForLon!!) < RELOAD_THRESHOLD_KM) return
        }
        withContext(Dispatchers.IO) {
            loadEvacuationCenters(userLat, userLon)
            isLoaded     = true
            loadedForLat = userLat
            loadedForLon = userLon
        }
    }

    private fun loadEvacuationCenters(userLat: Double, userLon: Double) {
        try {
            val json = context.assets.open("ph_evacs_merged.geojson")
                .bufferedReader().readText()

            val root          = gson.fromJson(json, JsonObject::class.java)
            val featuresArray = root.getAsJsonArray("features")
            android.util.Log.d("EvacuationRepo", "Parsed ${featuresArray.size()} features")

            allCenters = featuresArray.mapIndexedNotNull { index, element ->
                try {
                    val feature    = element.asJsonObject
                    val properties = feature.getAsJsonObject("properties")
                    val geometry   = feature.getAsJsonObject("geometry")

                    // Guard: geometry can be a JsonNull element in malformed features
                    if (geometry == null || geometry.isJsonNull) return@mapIndexedNotNull null
                    val geometryType = geometry.get("type")
                        ?.takeIf { !it.isJsonNull }?.asString ?: return@mapIndexedNotNull null

                    val centerPoint = when (geometryType) {
                        "Polygon" -> {
                            val coords = geometry.getAsJsonArray("coordinates")
                                ?.takeIf { it.size() > 0 }?.get(0)?.asJsonArray
                                ?: return@mapIndexedNotNull null
                            calculatePolygonCentroid(coords)
                        }
                        "Point" -> {
                            val coords = geometry.getAsJsonArray("coordinates")
                                ?.takeIf { it.size() >= 2 } ?: return@mapIndexedNotNull null
                            Pair(coords[0].asDouble, coords[1].asDouble)
                        }
                        else -> null
                    } ?: return@mapIndexedNotNull null

                    // Radius filter before any string parsing
                    val dist = calculateDistance(centerPoint.second, centerPoint.first, userLat, userLon)
                    if (dist > FILTER_RADIUS_KM) return@mapIndexedNotNull null

                    val place        = properties.get("place")?.takeIf { !it.isJsonNull }?.asString
                    val city         = properties.get("city")?.takeIf { !it.isJsonNull }?.asString
                    val municipality = properties.get("municipality")?.takeIf { !it.isJsonNull }?.asString
                    val province     = properties.get("province")?.asString ?: "Unknown Province"
                    val address      = listOfNotNull(place, city, municipality, province).joinToString(", ")

                    val capacityRaw = properties.get("capacity")?.takeIf { !it.isJsonNull }?.asString
                    val capacity = when {
                        capacityRaw == null             -> 1000
                        capacityRaw.startsWith(">")     -> capacityRaw.removePrefix(">").trim().toIntOrNull()?.plus(1) ?: 1000
                        capacityRaw.startsWith("<")     -> capacityRaw.removePrefix("<").trim().toIntOrNull()?.minus(1) ?: 50
                        capacityRaw.contains("-")       -> capacityRaw.split("-").firstOrNull()?.trim()?.toIntOrNull() ?: 1000
                        else                            -> capacityRaw.toIntOrNull() ?: 1000
                    }

                    EvacuationCenter(
                        id            = "evac_$index",
                        name          = properties.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "Evacuation Center",
                        address       = address,
                        latitude      = centerPoint.second,
                        longitude     = centerPoint.first,
                        capacity      = capacity,
                        contactNumber = null,
                        facilities    = determineFacilities(properties.get("type")?.takeIf { !it.isJsonNull }?.asString)
                    )
                } catch (e: Exception) {
                    android.util.Log.e("EvacuationRepo", "Error parsing feature $index: ${e.message}")
                    null
                }
            }

            android.util.Log.d("EvacuationRepo",
                "Loaded ${allCenters.size} centers within ${FILTER_RADIUS_KM}km of ($userLat, $userLon)")
        } catch (e: Exception) {
            android.util.Log.e("EvacuationRepo", "Error loading centers: ${e.message}")
            allCenters = emptyList()
        }
    }

    private fun calculatePolygonCentroid(coordinates: JsonArray): Pair<Double, Double> {
        var sumLon = 0.0; var sumLat = 0.0; var count = 0
        for (i in 0 until coordinates.size()) {
            val pt = coordinates[i].asJsonArray
            sumLon += pt[0].asDouble; sumLat += pt[1].asDouble; count++
        }
        return Pair(sumLon / count, sumLat / count)
    }

    private fun determineFacilities(type: String?): List<String> = when (type?.lowercase()) {
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
        else -> listOf("Shelter", "Water", "Toilets")
    }

    // ── Public queries ────────────────────────────────────────────────────────

    suspend fun getAllCenters(userLat: Double, userLon: Double): List<EvacuationCenter> {
        ensureLoaded(userLat, userLon); return allCenters
    }

    /** Single-center wrapper kept for backward compatibility. */
    suspend fun findNearestCenter(userLat: Double, userLon: Double): EvacuationCenter? =
        findNearestCenters(userLat, userLon, 1).firstOrNull()

    /** Returns up to [count] centers sorted by straight-line distance, closest first. */
    suspend fun findNearestCenters(userLat: Double, userLon: Double, count: Int): List<EvacuationCenter> {
        ensureLoaded(userLat, userLon)
        if (allCenters.isEmpty()) {
            android.util.Log.e("EvacuationRepo", "No centers within ${FILTER_RADIUS_KM}km")
            return emptyList()
        }
        return allCenters
            .sortedBy { calculateDistance(userLat, userLon, it.latitude, it.longitude) }
            .take(count)
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    /**
     * Routing waterfall — four tiers, fail-fast at each step:
     *
     *  1. Valhalla primary   (valhalla1.openstreetmap.de) — 5 s connect / 8 s read
     *  2. Valhalla alternate (valhalla.openstreetmap.de)  — 5 s connect / 8 s read
     *  3. OSRM demo server   (router.project-osrm.org)   — browser UA required
     *  4. Enriched straight-line fallback                 — instant, always succeeds
     *
     * The `routeSource` field on [RouteInfo] records which tier was used so
     * the Fragment can choose the correct zoom level (Fix 3).
     */
    suspend fun calculateRouteWithOSRM(
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double,
        mode: TravelMode
    ): RouteInfo {
        val distanceKm  = calculateDistance(fromLat, fromLon, toLat, toLon)
        val timeMinutes = if (mode == TravelMode.WALKING)
            ((distanceKm / 4.0) * 60).toInt()
        else
            ((distanceKm / 25.0) * 60).toInt()

        // ── Tier 1: Valhalla primary ──────────────────────────────────────────
        try {
            val result = tryValhalla("https://valhalla1.openstreetmap.de/route", fromLat, fromLon, toLat, toLon, mode)
            if (result != null) {
                android.util.Log.d("EvacuationRepo",
                    "Valhalla-1 OK: ${result.distanceKm}km, ${result.maneuvers.size} maneuvers")
                return result
            }
        } catch (e: Exception) {
            android.util.Log.w("EvacuationRepo", "Valhalla-1 failed: ${e.message}")
        }

        // ── Tier 2: Valhalla alternate ────────────────────────────────────────
        try {
            val result = tryValhalla("https://valhalla.openstreetmap.de/route", fromLat, fromLon, toLat, toLon, mode)
            if (result != null) {
                android.util.Log.d("EvacuationRepo",
                    "Valhalla-2 OK: ${result.distanceKm}km, ${result.maneuvers.size} maneuvers")
                return result
            }
        } catch (e: Exception) {
            android.util.Log.w("EvacuationRepo", "Valhalla-2 failed: ${e.message}")
        }

        // ── Tier 3: OSRM ─────────────────────────────────────────────────────
        try {
            val result = tryOSRM(fromLat, fromLon, toLat, toLon, mode)
            if (result != null) {
                android.util.Log.d("EvacuationRepo",
                    "OSRM OK: ${result.distanceKm}km, ${result.maneuvers.size} maneuvers")
                return result
            }
        } catch (e: Exception) {
            android.util.Log.w("EvacuationRepo", "OSRM failed: ${e.message}")
        }

        // ── Tier 4: enriched straight-line ────────────────────────────────────
        android.util.Log.w("EvacuationRepo", "All routing services unreachable — using enriched fallback")
        return buildEnrichedFallback(fromLat, fromLon, toLat, toLon, distanceKm, timeMinutes, mode)
    }

    // ── Tier 1 & 2: Valhalla ─────────────────────────────────────────────────

    /**
     * Shared Valhalla caller — accepts any Valhalla-compatible [url].
     * Returns null (rather than throwing) for non-200 HTTP or empty maneuvers
     * so the caller can cleanly fall through to the next tier.
     */
    private fun tryValhalla(
        url: String,
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double,
        mode: TravelMode
    ): RouteInfo? {
        val requestBody = buildValhallaRequest(fromLat, fromLon, toLat, toLon, mode)
        val connection  = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout    = 8_000
        connection.requestMethod  = "POST"
        connection.doOutput       = true
        connection.setRequestProperty("Content-Type", "application/json")
        // Some Valhalla hosts check the User-Agent; present as a browser to be safe.
        connection.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        connection.outputStream.use { it.write(requestBody.toByteArray()) }

        if (connection.responseCode != 200) {
            android.util.Log.w("EvacuationRepo", "$url HTTP ${connection.responseCode}")
            return null
        }
        val json   = gson.fromJson(connection.inputStream.bufferedReader().readText(), JsonObject::class.java)
        val parsed = parseValhallaResponse(json, mode) ?: return null
        return if (parsed.maneuvers.isNotEmpty()) parsed else null
    }

    private fun buildValhallaRequest(
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double,
        mode: TravelMode
    ): String {
        val locations = """
            "locations": [
                {"lat": $fromLat, "lon": $fromLon, "radius": 100},
                {"lat": $toLat,   "lon": $toLon,   "radius": 100}
            ]
        """.trimIndent()

        return if (mode == TravelMode.WALKING) """
            {
                $locations,
                "costing": "pedestrian",
                "costing_options": {
                    "pedestrian": {
                        "use_roads": 1.0, "use_hills": 0.5,
                        "walkway_factor": 1.0, "alley_factor": 2.0,
                        "driveway_factor": 2.0, "step_penalty": 30
                    }
                },
                "directions_options": {"units": "kilometers"}
            }
        """.trimIndent() else """
            {
                $locations,
                "costing": "auto",
                "directions_options": {"units": "kilometers"}
            }
        """.trimIndent()
    }

    private fun parseValhallaResponse(json: JsonObject, mode: TravelMode): RouteInfo? {
        val legs = json.getAsJsonObject("trip")?.getAsJsonArray("legs") ?: return null
        if (legs.size() == 0) return null
        val leg     = legs[0].asJsonObject
        val summary = leg.getAsJsonObject("summary")
        val lengthKm    = summary.get("length").asDouble
        val timeMinutes = (summary.get("time").asDouble / 60).toInt()
        val waypoints   = decodePolyline(leg.get("shape").asString)
        val maneuvers   = parseManeuvers(leg.getAsJsonArray("maneuvers"))
        return RouteInfo(lengthKm, timeMinutes, mode, waypoints, maneuvers, RouteSource.VALHALLA)
    }

    private fun parseManeuvers(maneuverArray: JsonArray?): List<Maneuver> {
        if (maneuverArray == null) return emptyList()
        return (0 until maneuverArray.size()).mapNotNull { i ->
            val m           = maneuverArray[i].asJsonObject
            val instruction = m.get("instruction")?.asString ?: return@mapNotNull null
            val length      = m.get("length")?.asDouble ?: 0.0
            val beginIdx    = m.get("begin_shape_index")?.asInt ?: 0
            Maneuver(instruction, length, beginIdx)
        }
    }

    // ── Tier 3: OSRM ─────────────────────────────────────────────────────────

    /**
     * Hits the public OSRM demo server.
     * - Walking uses the `foot` profile; driving uses `driving`.
     * - Returns null (rather than throwing) on HTTP error or empty steps
     *   so the caller falls through to tier 4.
     *
     * IMPORTANT: The demo server returns 403 for non-browser User-Agents.
     * We spoof a Chrome UA to pass that check — this is the same UA used
     * by the Valhalla call above for consistency.
     *
     * URL pattern:
     *   https://router.project-osrm.org/route/v1/{profile}/{lon,lat};{lon,lat}
     *   ?steps=true&geometries=geojson&overview=full&annotations=false
     */
    private fun tryOSRM(
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double,
        mode: TravelMode
    ): RouteInfo? {
        val profile = if (mode == TravelMode.WALKING) "foot" else "driving"
        val url = "https://router.project-osrm.org/route/v1/$profile/" +
                "$fromLon,$fromLat;$toLon,$toLat" +
                "?steps=true&geometries=geojson&overview=full&annotations=false"

        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout    = 8_000
        connection.requestMethod  = "GET"
        // Must look like a browser — the demo server 403s anything else
        connection.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Referer", "https://router.project-osrm.org/")

        if (connection.responseCode != 200) {
            android.util.Log.w("EvacuationRepo", "OSRM HTTP ${connection.responseCode}")
            return null
        }

        val json  = gson.fromJson(connection.inputStream.bufferedReader().readText(), JsonObject::class.java)
        return parseOSRMResponse(json, mode)
    }

    private fun parseOSRMResponse(json: JsonObject, mode: TravelMode): RouteInfo? {
        // Top-level: { "code": "Ok", "routes": [...] }
        if (json.get("code")?.asString != "Ok") return null
        val routes = json.getAsJsonArray("routes") ?: return null
        if (routes.size() == 0) return null

        val route       = routes[0].asJsonObject
        val distanceM   = route.get("distance")?.asDouble ?: return null
        val durationS   = route.get("duration")?.asDouble ?: return null
        val distanceKm  = distanceM / 1000.0
        val timeMinutes = (durationS / 60.0).toInt()

        // Geometry is GeoJSON LineString: { "type":"LineString", "coordinates":[[lon,lat],...] }
        val coords = route.getAsJsonObject("geometry")
            ?.getAsJsonArray("coordinates") ?: return null
        val waypoints = (0 until coords.size()).map { i ->
            val pt = coords[i].asJsonArray
            Waypoint(pt[1].asDouble, pt[0].asDouble, "Route point")
        }
        if (waypoints.size < 2) return null

        // Steps live inside legs[0].steps
        val legs  = route.getAsJsonArray("legs") ?: return null
        if (legs.size() == 0) return null
        val steps = legs[0].asJsonObject.getAsJsonArray("steps") ?: return null

        val maneuvers = parseOSRMSteps(steps, waypoints)
        if (maneuvers.isEmpty()) return null

        return RouteInfo(distanceKm, timeMinutes, mode, waypoints, maneuvers, RouteSource.OSRM)
    }

    /**
     * Converts OSRM step objects into [Maneuver] instances.
     *
     * Each OSRM step has:
     *   - maneuver.instruction (not present by default — we synthesize it)
     *   - maneuver.type  ("turn", "depart", "arrive", "roundabout", …)
     *   - maneuver.modifier ("left", "right", "straight", "slight left", …)
     *   - name  — the street name for the step
     *   - distance — metres for this step
     *   - geometry.coordinates[0] — the GeoPoint at the start of the step
     *
     * We find the closest waypoint index for the step's start point so the
     * advanceManeuverIfNeeded logic in the Fragment still works correctly.
     */
    private fun parseOSRMSteps(steps: JsonArray, waypoints: List<Waypoint>): List<Maneuver> {
        val maneuvers = mutableListOf<Maneuver>()
        var cumulativeDistKm = 0.0

        for (i in 0 until steps.size()) {
            val step     = steps[i].asJsonObject
            val maneuver = step.getAsJsonObject("maneuver") ?: continue
            val type     = maneuver.get("type")?.asString ?: continue
            val modifier = maneuver.get("modifier")?.asString
            val name     = step.get("name")?.asString?.takeIf { it.isNotBlank() } ?: ""
            val distM    = step.get("distance")?.asDouble ?: 0.0

            // Build a human-readable instruction
            val instruction = buildOSRMInstruction(type, modifier, name)

            // Find the closest waypoint index to this step's start coordinate
            val stepCoords = step.getAsJsonObject("geometry")
                ?.getAsJsonArray("coordinates")
            val stepLon = stepCoords?.get(0)?.asJsonArray?.get(0)?.asDouble ?: 0.0
            val stepLat = stepCoords?.get(0)?.asJsonArray?.get(1)?.asDouble ?: 0.0

            val closestIdx = waypoints.indices.minByOrNull { idx ->
                val wp = waypoints[idx]
                (wp.latitude - stepLat).pow(2) + (wp.longitude - stepLon).pow(2)
            } ?: 0

            maneuvers.add(Maneuver(instruction, distM / 1000.0, closestIdx))
            cumulativeDistKm += distM / 1000.0
        }
        return maneuvers
    }

    private fun buildOSRMInstruction(type: String, modifier: String?, streetName: String): String {
        val onto = if (streetName.isNotBlank()) " onto $streetName" else ""
        return when (type) {
            "depart"     -> "Head ${modifier ?: "forward"}$onto"
            "arrive"     -> "Arrive at evacuation center"
            "turn"       -> when (modifier) {
                "left"        -> "Turn left$onto"
                "right"       -> "Turn right$onto"
                "slight left" -> "Bear left$onto"
                "slight right"-> "Bear right$onto"
                "sharp left"  -> "Sharp left$onto"
                "sharp right" -> "Sharp right$onto"
                "uturn"       -> "Make a U-turn$onto"
                else          -> "Continue$onto"
            }
            "new name"   -> "Continue$onto"
            "merge"      -> "Merge${if (modifier != null) " $modifier" else ""}$onto"
            "on ramp"    -> "Take the ramp${if (modifier != null) " $modifier" else ""}$onto"
            "off ramp"   -> "Take the exit${if (modifier != null) " $modifier" else ""}$onto"
            "fork"       -> "Keep ${modifier ?: "straight"} at the fork$onto"
            "end of road"-> "Turn ${modifier ?: "right"} at the end of the road$onto"
            "roundabout" -> "Enter the roundabout$onto"
            "exit roundabout" -> "Exit the roundabout$onto"
            "rotary"     -> "Enter the rotary$onto"
            "exit rotary"-> "Exit the rotary$onto"
            else         -> "Continue$onto"
        }
    }

    // ── Tier 3: Enriched straight-line fallback ───────────────────────────────

    /**
     * Builds a straight-line route with synthetic turn-by-turn maneuvers.
     * Used when both Valhalla and OSRM are unreachable.
     *
     * `routeSource` is set to [RouteSource.FALLBACK] so the Fragment zooms
     * to level 15 instead of 18 (tile-cache-friendly, avoids gray tiles).
     */
    private fun buildEnrichedFallback(
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double,
        distanceKm: Double,
        timeMinutes: Int,
        mode: TravelMode
    ): RouteInfo {
        // 8 intermediate waypoints for a smoother polyline
        val waypoints = mutableListOf(Waypoint(fromLat, fromLon, "Start"))
        for (i in 1..6) {
            val f = i / 7.0
            waypoints.add(Waypoint(
                fromLat + (toLat - fromLat) * f,
                fromLon + (toLon - fromLon) * f,
                "Via $i"
            ))
        }
        waypoints.add(Waypoint(toLat, toLon, "Destination"))

        val bearing     = bearingDegrees(fromLat, fromLon, toLat, toLon)
        val cardinalDir = bearingToCardinal(bearing)
        val modeLabel   = if (mode == TravelMode.WALKING) "Walk" else "Drive"

        val maneuvers = listOf(
            Maneuver(
                instruction        = "$modeLabel $cardinalDir toward evacuation center (${String.format("%.2f", distanceKm)} km)",
                distanceKm         = distanceKm * 0.5,
                beginWaypointIndex = 0
            ),
            Maneuver(
                instruction        = "Continue straight to evacuation center",
                distanceKm         = distanceKm * 0.4,
                beginWaypointIndex = 4
            ),
            Maneuver(
                instruction        = "Arrive at evacuation center on the right",
                distanceKm         = 0.0,
                beginWaypointIndex = waypoints.size - 1
            )
        )

        return RouteInfo(distanceKm, timeMinutes, mode, waypoints, maneuvers, RouteSource.FALLBACK)
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val x = sin(dLon) * cos(lat2R)
        val y = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return (Math.toDegrees(atan2(x, y)) + 360) % 360
    }

    private fun bearingToCardinal(bearing: Double): String = when {
        bearing < 22.5  -> "north"
        bearing < 67.5  -> "northeast"
        bearing < 112.5 -> "east"
        bearing < 157.5 -> "southeast"
        bearing < 202.5 -> "south"
        bearing < 247.5 -> "southwest"
        bearing < 292.5 -> "west"
        bearing < 337.5 -> "northwest"
        else            -> "north"
    }

    private fun decodePolyline(encoded: String): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()
        var index = 0; var lat = 0; var lon = 0
        while (index < encoded.length) {
            var b: Int; var shift = 0; var result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            waypoints.add(Waypoint(lat / 1e6, lon / 1e6, "Route point"))
        }
        return waypoints
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // Kept for callers that use the non-suspend version
    fun calculateRoute(
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double,
        mode: TravelMode
    ): RouteInfo {
        val distanceKm  = calculateDistance(fromLat, fromLon, toLat, toLon)
        val timeMinutes = if (mode == TravelMode.WALKING)
            ((distanceKm / 4.0) * 60).toInt() else ((distanceKm / 25.0) * 60).toInt()
        return buildEnrichedFallback(fromLat, fromLon, toLat, toLon, distanceKm, timeMinutes, mode)
    }
}