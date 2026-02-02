package com.example.gabay.data.models

data class EmergencyContact(
    val id: String,
    val name: String,
    val number: String,
    val type: ContactType,
    val region: String? = null
)

enum class ContactType {
    EMERGENCY_HOTLINE, POLICE, FIRE,
    MEDICAL, DISASTER_RESPONSE, LOCAL_GOVERNMENT
}