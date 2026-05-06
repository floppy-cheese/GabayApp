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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class EvacuationViewModel(
    private val locationRepository: LocationRepository,
    private val evacuationRepository: EvacuationRepository
) : ViewModel() {

    // ── Public LiveData ───────────────────────────────────────────────────────

    private val _currentLocation   = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation

    private val _nearestCenter     = MutableLiveData<EvacuationCenter?>()
    val nearestCenter: LiveData<EvacuationCenter?> = _nearestCenter

    private val _routeInfo         = MutableLiveData<RouteInfo?>()
    val routeInfo: LiveData<RouteInfo?> = _routeInfo

    private val _currentSpeed      = MutableLiveData<Double>(0.0)
    val currentSpeed: LiveData<Double> = _currentSpeed

    private val _remainingDistance = MutableLiveData<Double?>()
    val remainingDistance: LiveData<Double?> = _remainingDistance

    private val _updatedETA        = MutableLiveData<Int?>()
    val updatedETA: LiveData<Int?> = _updatedETA

    private val _errorMessage      = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading         = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _alternatesRemaining = MutableLiveData(0)
    val alternatesRemaining: LiveData<Int> = _alternatesRemaining

    // ── Internal state ────────────────────────────────────────────────────────

    private var locationJob:    Job? = null
    private var etaTrackingJob: Job? = null
    private var routingJob:     Job? = null

    private var previousLocation: Location? = null
    private var previousTime:     Long = 0
    private val speedHistory = mutableListOf<Double>()
    private var currentTravelMode: TravelMode? = null

    private var rankedCenters: List<EvacuationCenter> = emptyList()
    private var currentCenterIndex = 0

    /**
     * Synchronous guard flag. Set to true BEFORE the coroutine launches so
     * rapid taps on "Find Center" can never spawn multiple routing jobs.
     * The Fragment checks this to disable the button immediately.
     */
    var isRoutingActive: Boolean = false
        private set

    companion object {
        private const val MAX_ALTERNATES = 6
    }

    init {
        startLocationTracking()
    }

    // ── Location tracking ─────────────────────────────────────────────────────

    private fun startLocationTracking() {
        locationJob = viewModelScope.launch {
            locationRepository.getCurrentLocation()
                .catch { _currentLocation.value = null }
                .collectLatest { location ->
                    _currentLocation.value = location
                    location?.let { calculateCurrentSpeed(it) }
                }
        }
    }

    private fun calculateCurrentSpeed(location: Location) {
        val currentTime = System.currentTimeMillis()
        val prev = previousLocation
        if (prev != null && previousTime > 0) {
            val timeDiffSeconds = (currentTime - previousTime) / 1000.0
            if (timeDiffSeconds >= 5.0) {
                val distance = prev.distanceTo(location).toDouble()
                if (distance >= 10.0) {
                    val speedKmh = (distance / timeDiffSeconds) * 3.6
                    if (speedKmh < 150) updateSpeedHistory(speedKmh)
                } else {
                    updateSpeedHistory(0.0)
                }
                previousLocation = location
                previousTime     = currentTime
            }
        } else {
            previousLocation = location
            previousTime     = currentTime
        }
    }

    private fun updateSpeedHistory(speed: Double) {
        speedHistory.add(speed)
        if (speedHistory.size > 10) speedHistory.removeAt(0)
        val avgSpeed = if (speedHistory.size >= 3)
            speedHistory.sorted().drop(1).dropLast(1).average()
        else
            speedHistory.average()
        _currentSpeed.value = avgSpeed
    }

    // ── Route finding ─────────────────────────────────────────────────────────

    /**
     * Primary entry-point called by the Fragment (button tap or auto-launch).
     *
     * [isRoutingActive] is set synchronously before the coroutine launches so
     * no amount of rapid taps can create concurrent routing jobs.
     */
    fun findNearestCenter(travelMode: TravelMode) {
        // Hard guard — synchronous, checked before any async work
        if (isRoutingActive) {
            android.util.Log.d("EvacuationVM", "findNearestCenter ignored — already routing")
            return
        }

        val location = _currentLocation.value
        if (location == null) {
            _errorMessage.value = "Unable to get your location. Please enable location services."
            return
        }

        // Mark active BEFORE launching — prevents race between quick successive calls
        isRoutingActive      = true
        routingJob?.cancel()
        currentTravelMode    = travelMode
        currentCenterIndex   = 0
        rankedCenters        = emptyList()
        _alternatesRemaining.value = 0

        routingJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val centers = withContext(Dispatchers.IO) {
                    evacuationRepository.findNearestCenters(
                        location.latitude, location.longitude, MAX_ALTERNATES
                    )
                }
                if (centers.isEmpty()) {
                    _errorMessage.value = "No evacuation centers found nearby."
                    return@launch
                }
                rankedCenters = centers
                _alternatesRemaining.value = (centers.size - 1).coerceAtLeast(0)
                routeToCenter(location, centers[0], travelMode)
            } catch (e: Exception) {
                _errorMessage.value = "Error finding centers: ${e.message}"
            } finally {
                _isLoading.value = false
                isRoutingActive  = false
            }
        }
    }

    fun switchToNextCenter(travelMode: TravelMode) {
        if (isRoutingActive) return
        if (currentCenterIndex >= rankedCenters.size - 1) {
            _errorMessage.value = "No more alternate routes available."
            return
        }

        val location = _currentLocation.value ?: run {
            _errorMessage.value = "Unable to get your location."
            return
        }

        isRoutingActive = true
        routingJob?.cancel()
        currentCenterIndex++
        _alternatesRemaining.value = (rankedCenters.size - 1 - currentCenterIndex).coerceAtLeast(0)

        val nextCenter = rankedCenters[currentCenterIndex]
        android.util.Log.d("EvacuationVM",
            "Switching to alternate #$currentCenterIndex: ${nextCenter.name}")

        routingJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                routeToCenter(location, nextCenter, travelMode)
            } catch (e: Exception) {
                _errorMessage.value = "Error calculating alternate route: ${e.message}"
            } finally {
                _isLoading.value = false
                isRoutingActive  = false
            }
        }
    }

    /**
     * Shared routing core. Runs the Valhalla call on IO.
     * No retry loop here — the Repository handles a single attempt with a
     * 5 s timeout and returns an enriched fallback (with synthetic maneuvers)
     * if Valhalla is unreachable. Fail-fast is better than 30 s of retries.
     */
    private suspend fun routeToCenter(
        location: Location,
        center: EvacuationCenter,
        travelMode: TravelMode
    ) {
        _nearestCenter.postValue(center)

        val route = withContext(Dispatchers.IO) {
            evacuationRepository.calculateRouteWithOSRM(
                location.latitude, location.longitude,
                center.latitude,   center.longitude,
                travelMode
            )
        }

        android.util.Log.d("EvacuationVM",
            "Route ready for ${center.name}: ${route.distanceKm}km, ${route.maneuvers.size} maneuvers")
        _routeInfo.postValue(route)
    }

    fun clearRoute() {
        routingJob?.cancel()
        routingJob           = null
        isRoutingActive      = false
        stopETATracking()
        _routeInfo.value         = null
        _nearestCenter.value     = null
        _updatedETA.value        = null
        _remainingDistance.value = null
        _alternatesRemaining.value = 0
        currentTravelMode        = null
        rankedCenters            = emptyList()
        currentCenterIndex       = 0
    }

    // ── ETA tracking ──────────────────────────────────────────────────────────

    fun startETATracking() {
        etaTrackingJob?.cancel()
        etaTrackingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000)
                val location = _currentLocation.value
                val center   = _nearestCenter.value
                val speed    = _currentSpeed.value ?: 0.0
                val mode     = currentTravelMode
                if (location != null && center != null && mode != null) {
                    val remaining = calculateDistance(
                        location.latitude, location.longitude,
                        center.latitude,   center.longitude
                    )
                    _remainingDistance.postValue(remaining)
                    val effectiveSpeed = if (speed > 1.0) speed
                    else if (mode == TravelMode.WALKING) 4.0 else 25.0
                    _updatedETA.postValue(((remaining / effectiveSpeed) * 60).toInt())
                }
            }
        }
    }

    fun stopETATracking() {
        etaTrackingJob?.cancel()
        etaTrackingJob   = null
        speedHistory.clear()
        previousLocation = null
        previousTime     = 0
        _currentSpeed.value = 0.0
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    override fun onCleared() {
        super.onCleared()
        routingJob?.cancel()
        stopETATracking()
        locationJob?.cancel()
        locationJob            = null
        isRoutingActive        = false
        _nearestCenter.value   = null
        _routeInfo.value       = null
        _errorMessage.value    = null
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