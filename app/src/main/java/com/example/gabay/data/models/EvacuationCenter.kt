package com.example.gabay.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EvacuationCenter(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val capacity: Int,
    val contactNumber: String?,
    val facilities: List<String>
) : Parcelable

data class RouteInfo(
    val distanceKm: Double,
    val estimatedTimeMinutes: Int,
    val travelMode: TravelMode,
    val waypoints: List<Waypoint>
)

data class Waypoint(
    val latitude: Double,
    val longitude: Double,
    val instruction: String
)

enum class TravelMode {
    WALKING, VEHICLE
}