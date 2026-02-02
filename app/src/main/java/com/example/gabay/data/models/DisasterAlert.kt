package com.example.gabay.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisasterAlert(
    val id: String,
    val type: DisasterType,
    val description: String,
    val distanceKm: Double,
    val severity: Severity,
    val isActive: Boolean
) : Parcelable

enum class DisasterType {
    EARTHQUAKE, TYPHOON, FLOOD, NONE
}

enum class Severity {
    LOW, MODERATE, HIGH, CRITICAL
}