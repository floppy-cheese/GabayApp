package com.example.gabay.data.models

data class PhoneNumber(
    val number: String,
    val label: String
)

data class EmergencyContact(
    val id: String,
    val name: String,
    val numbers: List<PhoneNumber>,
    val type: ContactType,
    val region: String? = null
) {
    val primaryNumber: String get() = numbers.firstOrNull()?.number ?: ""
}

enum class ContactType {
    EMERGENCY_HOTLINE, POLICE, FIRE,
    MEDICAL, DISASTER_RESPONSE
}