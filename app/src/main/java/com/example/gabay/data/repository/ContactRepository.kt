package com.example.gabay.data.repository

import android.content.Context
import com.example.gabay.data.models.ContactType
import com.example.gabay.data.models.EmergencyContact

class ContactRepository(private val context: Context) {

    fun getNationwideContacts(): List<EmergencyContact> = listOf(
        EmergencyContact("1", "Emergency Hotline", "911",
            ContactType.EMERGENCY_HOTLINE),
        EmergencyContact("2", "NDRRMC", "0917-5091286",
            ContactType.DISASTER_RESPONSE),
        EmergencyContact("3", "PNP Hotline", "117",
            ContactType.POLICE),
        EmergencyContact("4", "BFP Hotline", "0932-8773403",
            ContactType.FIRE),
        EmergencyContact("5", "Red Cross", "143",
            ContactType.MEDICAL),
        EmergencyContact("6", "DOH Emergency", "1555",
            ContactType.MEDICAL),
        EmergencyContact("7", "PAGASA Weather", "0922-8992710",
            ContactType.DISASTER_RESPONSE)
    )

    fun getLocalContacts(region: String): List<EmergencyContact> = listOf(
        EmergencyContact("8", "Calamba CDRRMO", "049-545-6789",
            ContactType.DISASTER_RESPONSE, "Calabarzon"),
        EmergencyContact("9", "Calamba Police", "049-545-1234",
            ContactType.POLICE, "Calabarzon"),
        EmergencyContact("10", "Calamba Fire", "049-545-3456",
            ContactType.FIRE, "Calabarzon")
    )
}