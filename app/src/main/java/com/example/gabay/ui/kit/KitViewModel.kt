package com.example.gabay.ui.kit

import androidx.lifecycle.*
import com.example.gabay.data.repository.KitRepository
import com.example.gabay.data.models.EmergencyKitItem

data class PreparednessStatus(
    val level: String,
    val percentage: Int,
    val color: Int
)

class KitViewModel(
    private val kitRepository: KitRepository
) : ViewModel() {

    private val _householdSize = MutableLiveData(1)
    val householdSize: LiveData<Int> = _householdSize

    private val _basicItems = MutableLiveData<List<EmergencyKitItem>>()
    val basicItems: LiveData<List<EmergencyKitItem>> = _basicItems

    private val _additionalItems = MutableLiveData<List<EmergencyKitItem>>()
    val additionalItems: LiveData<List<EmergencyKitItem>> = _additionalItems

    private val _preparednessStatus = MutableLiveData<PreparednessStatus>()
    val preparednessStatus: LiveData<PreparednessStatus> = _preparednessStatus

    init {
        loadKitItems()
        android.util.Log.d("KitViewModel", "ViewModel initialized")
    }

    private fun loadKitItems() {
        val currentSize = _householdSize.value ?: 1

        val basicItemsFromRepo = kitRepository.getBasicKitItems()
        android.util.Log.d("KitViewModel", "Loaded ${basicItemsFromRepo.size} basic items from repository")

        val basicWithQuantities = basicItemsFromRepo.map { item ->
            item.copy(
                calculatedQuantity = kitRepository.calculateQuantity(
                    currentSize,
                    item.quantityFormula
                ),
                isChecked = false
            )
        }

        _basicItems.value = basicWithQuantities

        val additionalItemsFromRepo = kitRepository.getAdditionalKitItems()
        android.util.Log.d("KitViewModel", "Loaded ${additionalItemsFromRepo.size} additional items from repository")

        val additionalWithQuantities = additionalItemsFromRepo.map { item ->
            item.copy(
                calculatedQuantity = kitRepository.calculateQuantity(
                    currentSize,
                    item.quantityFormula
                ),
                isChecked = false
            )
        }

        _additionalItems.value = additionalWithQuantities

        updatePreparednessStatus()
    }

    fun increaseHouseholdSize() {
        val current = _householdSize.value ?: 1
        if (current < 20) {
            _householdSize.value = current + 1
            updateQuantitiesOnly()
        }
    }

    fun decreaseHouseholdSize() {
        val current = _householdSize.value ?: 1
        if (current > 1) {
            _householdSize.value = current - 1
            updateQuantitiesOnly()
        }
    }

    private fun updateQuantitiesOnly() {
        val currentSize = _householdSize.value ?: 1

        _basicItems.value = _basicItems.value?.map { item ->
            item.copy(
                calculatedQuantity = kitRepository.calculateQuantity(
                    currentSize,
                    item.quantityFormula
                )
            )
        }

        _additionalItems.value = _additionalItems.value?.map { item ->
            item.copy(
                calculatedQuantity = kitRepository.calculateQuantity(
                    currentSize,
                    item.quantityFormula
                )
            )
        }
    }

    fun updateItemChecked(item: EmergencyKitItem, isChecked: Boolean) {
        android.util.Log.d("KitViewModel", "Updating item: ${item.name}, checked: $isChecked")

        if (item.isBasic) {
            _basicItems.value = _basicItems.value?.map {
                if (it.id == item.id) {
                    it.copy(isChecked = isChecked)
                } else {
                    it
                }
            }
        } else {
            _additionalItems.value = _additionalItems.value?.map {
                if (it.id == item.id) {
                    it.copy(isChecked = isChecked)
                } else {
                    it
                }
            }
        }
        updatePreparednessStatus()
    }

    private fun updatePreparednessStatus() {
        val total = _basicItems.value?.size ?: 0
        val checked = _basicItems.value?.count { it.isChecked } ?: 0

        val percentage = if (total > 0) (checked * 100) / total else 0

        android.util.Log.d("KitViewModel", "Preparedness: $checked/$total = $percentage%")

        val status = when {
            percentage < 33 -> PreparednessStatus(
                "Basic Preparedness ($percentage%)",
                percentage,
                android.graphics.Color.parseColor("#F44336")
            )
            percentage < 66 -> PreparednessStatus(
                "Moderate Preparedness ($percentage%)",
                percentage,
                android.graphics.Color.parseColor("#FF9800")
            )
            else -> PreparednessStatus(
                "High Preparedness ($percentage%)",
                percentage,
                android.graphics.Color.parseColor("#4CAF50")
            )
        }

        _preparednessStatus.value = status
    }

    override fun onCleared() {
        super.onCleared()
        android.util.Log.d("KitViewModel", "ViewModel cleared")
    }
}

class KitViewModelFactory(
    private val kitRepository: KitRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KitViewModel(kitRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}