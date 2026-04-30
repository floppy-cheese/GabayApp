package com.example.gabay.ui.home

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gabay.data.models.DisasterAlert
import com.example.gabay.data.repository.DisasterRepository
import com.example.gabay.data.repository.LocationRepository
import com.example.gabay.data.repository.LocationInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val locationRepository: LocationRepository,
    private val disasterRepository: DisasterRepository
) : ViewModel() {

    private val _currentLocation = MutableLiveData<Location?>(null)
    val currentLocation: LiveData<Location?> = _currentLocation

    private val _locationInfo = MutableLiveData<LocationInfo?>()
    val locationInfo: LiveData<LocationInfo?> = _locationInfo

    private val _disasterAlerts = MutableLiveData<List<DisasterAlert>>()
    val disasterAlerts: LiveData<List<DisasterAlert>> = _disasterAlerts

    private val _isLoadingAlerts = MutableLiveData<Boolean>(false)
    val isLoadingAlerts: LiveData<Boolean> = _isLoadingAlerts

    private val _isLiveFetch = MutableLiveData<Boolean>(false)
    val isLiveFetch: LiveData<Boolean> = _isLiveFetch

    private var locationJob: Job? = null
    private var refreshJob: Job? = null
    private var isFirstLoad = true

    private var lastFetchTime = 0L

    companion object {
        const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    init {
        observeLocation()
    }

    private fun observeLocation() {
        locationJob = viewModelScope.launch {
            locationRepository.getCurrentLocation()
                .catch { _currentLocation.value = null }
                .collectLatest { location ->
                    if (location != null) {
                        _currentLocation.value = location
                        val info = locationRepository.getLocationInfo(
                            location.latitude, location.longitude
                        )
                        _locationInfo.value = info
                        fetchDisasterAlerts(location)
                    }
                }
        }
    }

    fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                val loc = _currentLocation.value
                if (loc != null) {
                    fetchDisasterAlerts(loc)
                }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun fetchDisasterAlerts(location: Location? = _currentLocation.value) {
        val now = System.currentTimeMillis()
        // Don't fetch if we fetched less than 60 seconds ago
        if (now - lastFetchTime < 60_000L && lastFetchTime != 0L) return

        if (location == null) {
            _isLoadingAlerts.value = false
            return
        }

        lastFetchTime = now
        viewModelScope.launch {
            _isLoadingAlerts.value = true
            val alerts = disasterRepository.getDisasterAlerts(location)
            _disasterAlerts.value = alerts
            _isLiveFetch.value = true
            isFirstLoad = false
            _isLoadingAlerts.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        refreshJob?.cancel()
        locationJob = null
        refreshJob = null
        _currentLocation.value = null
        _locationInfo.value = null
    }
}

class HomeViewModelFactory(
    private val locationRepository: LocationRepository,
    private val disasterRepository: DisasterRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(locationRepository, disasterRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}