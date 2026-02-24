package com.example.gabay.ui.evacuation

import android.location.Location
import androidx.lifecycle.*
import com.example.gabay.data.repository.LocationRepository
import com.example.gabay.data.repository.EvacuationRepository
import com.example.gabay.data.models.EvacuationCenter
import com.example.gabay.data.models.RouteInfo
import com.example.gabay.data.models.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class EvacuationViewModel(
    private val locationRepository: LocationRepository,
    private val evacuationRepository: EvacuationRepository
) : ViewModel() {

    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation

    private val _remainingDistance = MutableLiveData<Double?>()
    val remainingDistance: LiveData<Double?> = _remainingDistance

    private val _nearestCenter = MutableLiveData<EvacuationCenter?>()
    val nearestCenter: LiveData<EvacuationCenter?> = _nearestCenter

    private val _routeInfo = MutableLiveData<RouteInfo?>()
    val routeInfo: LiveData<RouteInfo?> = _routeInfo

    private val _currentSpeed = MutableLiveData<Double>(0.0) // km/h
    val currentSpeed: LiveData<Double> = _currentSpeed

    private val _updatedETA = MutableLiveData<Int?>()
    val updatedETA: LiveData<Int?> = _updatedETA

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var locationJob: Job? = null
    private var etaTrackingJob: Job? = null

    private var previousLocation: Location? = null
    private var previousTime: Long = 0
    private val speedHistory = mutableListOf<Double>()
    private var currentTravelMode: TravelMode? = null

    init {
        startLocationTracking()
    }

    private fun startLocationTracking() {
        locationJob = viewModelScope.launch {
            locationRepository.getCurrentLocation()
                .catch { e ->
                    _currentLocation.value = null
                }
                .collectLatest { location ->
                    _currentLocation.value = location
                    location?.let {
                        calculateCurrentSpeed(it)
                    }
                }
        }
    }

    private fun calculateCurrentSpeed(location: Location) {
        val currentTime = System.currentTimeMillis()

        // Use GPS speed if available and reliable (> 0.5 m/s)
        if (location.hasSpeed() && location.speed > 0.5f) {
            // Convert m/s to km/h
            val speedKmh = (location.speed * 3.6).toDouble()
            updateSpeedHistory(speedKmh)
            android.util.Log.d("EvacuationVM", "GPS Speed: ${String.format("%.1f", speedKmh)} km/h")
        } else if (previousLocation != null && previousTime > 0) {
            // Calculate speed from position change
            val timeDiffSeconds = (currentTime - previousTime) / 1000.0

            if (timeDiffSeconds > 2) { // Only calculate if at least 2 seconds passed
                val distance = previousLocation!!.distanceTo(location).toDouble() // meters
                val speedMs = distance / timeDiffSeconds
                val speedKmh = speedMs * 3.6

                // Filter out unrealistic speeds (> 150 km/h)
                if (speedKmh < 150) {
                    updateSpeedHistory(speedKmh)
                    android.util.Log.d("EvacuationVM", "Calculated Speed: ${String.format("%.1f", speedKmh)} km/h")
                }

                previousLocation = location
                previousTime = currentTime
            }
        } else {
            previousLocation = location
            previousTime = currentTime
        }
    }

    private fun updateSpeedHistory(speed: Double) {
        speedHistory.add(speed)

        // Keep only last 10 speed measurements
        if (speedHistory.size > 10) {
            speedHistory.removeAt(0)
        }

        // Calculate average speed (removes outliers)
        val avgSpeed = if (speedHistory.size >= 3) {
            speedHistory.sorted().drop(1).dropLast(1).average()
        } else {
            speedHistory.average()
        }

        _currentSpeed.value = avgSpeed
    }

    fun startETATracking() {
        etaTrackingJob = viewModelScope.launch {
            while (true) {
                delay(3000) // Update every 3 seconds

                val location = _currentLocation.value
                val center = _nearestCenter.value
                val speed = _currentSpeed.value ?: 0.0
                val mode = currentTravelMode

                if (location != null && center != null && mode != null) {
                    val remainingDistance = calculateDistance(
                        location.latitude,
                        location.longitude,
                        center.latitude,
                        center.longitude
                    )

                    _remainingDistance.postValue(remainingDistance)

                    val effectiveSpeed = if (speed > 1.0) speed
                    else if (mode == TravelMode.WALKING) 4.0 else 25.0

                    val etaMinutes = ((remainingDistance / effectiveSpeed) * 60).toInt()

                    _updatedETA.postValue(etaMinutes)

                    android.util.Log.d("EvacuationVM",
                        "Live update — dist: ${String.format("%.2f", remainingDistance)}km, " +
                                "speed: ${String.format("%.1f", speed)}km/h, ETA: ${etaMinutes}min")
                }
            }
        }
    }

    fun stopETATracking() {
        etaTrackingJob?.cancel()
        etaTrackingJob = null
        speedHistory.clear()
        previousLocation = null
        previousTime = 0
        _currentSpeed.value = 0.0
    }

    fun findNearestCenter(travelMode: TravelMode) {
        currentTravelMode = travelMode
        val location = _currentLocation.value

        if (location == null) {
            _errorMessage.value = "Unable to get your location. Please enable location services."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Find nearest center
                val center = withContext(Dispatchers.IO) {
                    evacuationRepository.findNearestCenter(
                        location.latitude,
                        location.longitude
                    )
                }

                if (center == null) {
                    _errorMessage.value = "No evacuation centers found in the database."
                    _isLoading.value = false
                    return@launch
                }

                _nearestCenter.value = center

                // Calculate route with Valhalla
                val route = withContext(Dispatchers.IO) {
                    evacuationRepository.calculateRouteWithOSRM(
                        location.latitude,
                        location.longitude,
                        center.latitude,
                        center.longitude,
                        travelMode
                    )
                }

                _routeInfo.value = route

            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearRoute() {
        stopETATracking()
        _routeInfo.value = null
        _nearestCenter.value = null
        _updatedETA.value = null
        _remainingDistance.value = null  // add this line
        currentTravelMode = null
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

    override fun onCleared() {
        super.onCleared()
        stopETATracking()
        locationJob?.cancel()
        locationJob = null
        _nearestCenter.value = null
        _routeInfo.value = null
        _errorMessage.value = null
        _currentLocation.value = null
    }
}

class EvacuationViewModelFactory(
    private val locationRepository: LocationRepository,
    private val evacuationRepository: EvacuationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EvacuationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EvacuationViewModel(locationRepository, evacuationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}