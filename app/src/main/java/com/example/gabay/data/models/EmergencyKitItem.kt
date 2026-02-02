package com.example.gabay.data.models

data class EmergencyKitItem(
    val id: String,
    val name: String,
    val description: String,
    val quantityFormula: String,
    val isBasic: Boolean,
    val category: KitCategory,
    var isChecked: Boolean = false,
    var calculatedQuantity: String = ""
)

enum class KitCategory {
    WATER_FOOD, TOOLS, SANITATION, COMMUNICATION,
    MEDICAL, INFANT, PET, ELDERLY, DOCUMENTS, CLOTHING
}