package com.example.gabay.data.models

// ── RouteSource ──────────────────────────────────────────────────────────────
//
// Records which routing tier produced a RouteInfo so the Fragment can choose
// an appropriate map zoom level:
//   VALHALLA / OSRM → zoom 18 (real turn-by-turn, road-snapped polyline)
//   FALLBACK        → zoom 15 (straight-line, uncached tiles at 18 look gray)
//
enum class RouteSource {
    VALHALLA,
    OSRM,
    FALLBACK
}

// ── Core models ───────────────────────────────────────────────────────────────

data class RouteInfo(
    val distanceKm: Double,
    val estimatedTimeMinutes: Int,
    val travelMode: TravelMode,
    val waypoints: List<Waypoint>,
    val maneuvers: List<Maneuver>,
    /** Which routing service produced this route. Defaults to FALLBACK for
     *  backward-compatibility with any existing call-sites that don't supply it. */
    val routeSource: RouteSource = RouteSource.FALLBACK
)

data class Waypoint(
    val latitude: Double,
    val longitude: Double,
    val name: String
)

data class Maneuver(
    val instruction: String,
    val distanceKm: Double,
    val beginWaypointIndex: Int
)

data class EvacuationCenter(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val capacity: Int,
    val contactNumber: String?,
    val facilities: List<String>
)

enum class TravelMode {
    WALKING,
    VEHICLE
}