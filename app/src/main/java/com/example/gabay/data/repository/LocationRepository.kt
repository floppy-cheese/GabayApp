package com.example.gabay.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val barangay: String,
    val city: String,
    val province: String
)

class LocationRepository(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocoder = Geocoder(context, Locale("en", "PH"))

    fun getCurrentLocation(): Flow<Location?> = callbackFlow {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).apply {
            setMinUpdateIntervalMillis(5000L)
            setMaxUpdateDelayMillis(15000L)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            trySend(null)
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
        }
    }

    suspend fun getLocationInfo(latitude: Double, longitude: Double): LocationInfo {
        return try {
            val addresses = getAddressFromLocation(latitude, longitude, maxResults = 3)

            if (addresses.isNotEmpty()) {
                for (address in addresses) {
                    val locationInfo = extractLocationInfo(address, latitude, longitude)

                    if (!locationInfo.barangay.contains("Road") &&
                        locationInfo.barangay != "Unknown Barangay" &&
                        !locationInfo.barangay.startsWith("Unnamed")) {
                        android.util.Log.d("LocationRepo", "Found good address: ${locationInfo.barangay}, ${locationInfo.city}, ${locationInfo.province}")
                        return locationInfo
                    }
                }

                val firstAddress = addresses[0]
                val locationInfo = extractLocationInfo(firstAddress, latitude, longitude)

                android.util.Log.d("LocationRepo", "Using fallback address")
                logAddressDetails(firstAddress)

                return locationInfo
            } else {
                android.util.Log.w("LocationRepo", "No addresses found for coordinates")
                return LocationInfo(
                    latitude,
                    longitude,
                    "Unknown Barangay",
                    "Unknown City",
                    "Unknown Province"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationRepo", "Geocoding error: ${e.message}")
            e.printStackTrace()
            return LocationInfo(
                latitude,
                longitude,
                "Unknown Barangay",
                "Unknown City",
                "Unknown Province"
            )
        }
    }

    private fun extractLocationInfo(address: Address, lat: Double, lon: Double): LocationInfo {
        // Log all available fields to debug
        android.util.Log.d("LocationRepo", "=== Address Details ===")
        android.util.Log.d("LocationRepo", "Feature Name: ${address.featureName}")
        android.util.Log.d("LocationRepo", "Thoroughfare: ${address.thoroughfare}")
        android.util.Log.d("LocationRepo", "SubThoroughfare: ${address.subThoroughfare}")
        android.util.Log.d("LocationRepo", "SubLocality: ${address.subLocality}")
        android.util.Log.d("LocationRepo", "Locality: ${address.locality}")
        android.util.Log.d("LocationRepo", "SubAdminArea: ${address.subAdminArea}")
        android.util.Log.d("LocationRepo", "AdminArea: ${address.adminArea}")
        android.util.Log.d("LocationRepo", "Premises: ${address.premises}")

        val barangay = when {
            !address.thoroughfare.isNullOrBlank() -> {
                address.thoroughfare.replace(" Road", "").trim()
            }

            !address.subLocality.isNullOrBlank() &&
                    !address.subLocality.equals("Road", ignoreCase = true) ->
                address.subLocality

            !address.premises.isNullOrBlank() ->
                address.premises

            else -> "Unknown Area"
        }

        val city = when {
            !address.locality.isNullOrBlank() -> address.locality
            !address.subAdminArea.isNullOrBlank() -> address.subAdminArea
            else -> "Unknown City"
        }

        val province = when {
            !address.subAdminArea.isNullOrBlank() -> address.subAdminArea
            !address.adminArea.isNullOrBlank() -> address.adminArea
            else -> "Unknown Province"
        }

        return LocationInfo(lat, lon, barangay, city, province)
    }

    private fun logAddressDetails(address: Address) {
        android.util.Log.d("LocationRepo", "Full address line: ${address.getAddressLine(0)}")
        for (i in 0 until address.maxAddressLineIndex + 1) {
            android.util.Log.d("LocationRepo", "Address line $i: ${address.getAddressLine(i)}")
        }
    }

    private suspend fun getAddressFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int = 1
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, maxResults) { addresses ->
                    continuation.resume(addresses)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, maxResults) ?: emptyList()
                continuation.resume(addresses)
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationRepo", "Geocoder exception: ${e.message}")
            continuation.resume(emptyList())
        }
    }

    fun getLocationString(lat: Double, lon: Double): String {
        return "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}"
    }
}