package com.example.gabay.data.models
enum class RouteSource {
    VALHALLA,
    OSRM,
    FALLBACK
}

data class RouteInfo(
    val distanceKm: Double,
    val estimatedTimeMinutes: Int,
    val travelMode: TravelMode,
    val waypoints: List<Waypoint>,
    val maneuvers: List<Maneuver>,
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