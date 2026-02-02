package com.example.gabay.ui.home

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gabay.data.repository.LocationRepository
import com.example.gabay.data.repository.LocationInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _currentLocation = MutableLiveData<Location?>(null)
    val currentLocation: LiveData<Location?> = _currentLocation

    private val _locationInfo = MutableLiveData<LocationInfo?>()
    val locationInfo: LiveData<LocationInfo?> = _locationInfo

    private var locationJob: Job? = null

    init {
        observeLocation()
    }

    private fun observeLocation() {
        locationJob = viewModelScope.launch {
            locationRepository.getCurrentLocation()
                .catch { e ->
                    _currentLocation.value = null
                }
                .collectLatest { location ->
                    _currentLocation.value = location
                    location?.let {
                        // Get full address info
                        val info = locationRepository.getLocationInfo(
                            it.latitude,
                            it.longitude
                        )
                        _locationInfo.value = info
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        locationJob = null
        _currentLocation.value = null
        _locationInfo.value = null
    }
}

class HomeViewModelFactory(
    private val locationRepository: LocationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(locationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}