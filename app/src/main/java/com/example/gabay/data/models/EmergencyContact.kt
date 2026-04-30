package com.example.gabay.data.models

data class PhoneNumber(
    val number: String,
    val label: String  // e.g. "Hotline", "Globe", "Smart", "Landline", "Trunk Line"
)

data class EmergencyContact(
    val id: String,
    val name: String,
    val numbers: List<PhoneNumber>,
    val type: ContactType,
    val region: String? = null
) {
    // Convenience: primary number is always the first in the list
    val primaryNumber: String get() = numbers.firstOrNull()?.number ?: ""
}

enum class ContactType {
    EMERGENCY_HOTLINE, POLICE, FIRE,
    MEDICAL, DISASTER_RESPONSE, LOCAL_GOVERNMENT
}