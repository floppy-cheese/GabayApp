package com.example.gabay.data.repository

import android.content.Context
import com.example.gabay.data.models.ContactType
import com.example.gabay.data.models.EmergencyContact
import com.example.gabay.data.models.PhoneNumber

class ContactRepository(private val context: Context) {

    fun getNationwideContacts(): List<EmergencyContact> = listOf(

        EmergencyContact(
            id = "1",
            name = "Emergency Hotline",
            numbers = listOf(
                PhoneNumber("911", "Hotline")
            ),
            type = ContactType.EMERGENCY_HOTLINE
        ),

        EmergencyContact(
            id = "2",
            name = "NDRRMC",
            numbers = listOf(
                PhoneNumber("0917-509-1286", "Globe"),
                PhoneNumber("(02) 8911-5061", "Landline"),
                PhoneNumber("(02) 8911-5062", "Landline 2")
            ),
            type = ContactType.DISASTER_RESPONSE
        ),

        EmergencyContact(
            id = "3",
            name = "PNP Hotline",
            numbers = listOf(
                PhoneNumber("117", "Hotline"),
                PhoneNumber("(02) 8722-0650", "Landline"),
                PhoneNumber("0919-777-7377", "Text Hotline")
            ),
            type = ContactType.POLICE
        ),

        EmergencyContact(
            id = "4",
            name = "BFP Hotline",
            numbers = listOf(
                PhoneNumber("0932-877-3403", "Globe"),
                PhoneNumber("(02) 8426-0219", "Landline"),
                PhoneNumber("(02) 8426-3812", "Landline 2")
            ),
            type = ContactType.FIRE
        ),

        EmergencyContact(
            id = "5",
            name = "Philippine Red Cross",
            numbers = listOf(
                PhoneNumber("143", "Hotline"),
                PhoneNumber("0917-124-4143", "Globe"),
                PhoneNumber("(02) 8527-8385", "Landline")
            ),
            type = ContactType.MEDICAL
        ),

        EmergencyContact(
            id = "6",
            name = "DOH Emergency",
            numbers = listOf(
                PhoneNumber("1555", "Hotline"),
                PhoneNumber("(02) 8651-7800", "Landline"),
                PhoneNumber("0917-803-1057", "Globe")
            ),
            type = ContactType.MEDICAL
        ),

        EmergencyContact(
            id = "7",
            name = "PAGASA",
            numbers = listOf(
                PhoneNumber("0922-899-2710", "Smart"),
                PhoneNumber("(02) 8284-0800", "Landline"),
                PhoneNumber("(02) 8927-1335", "Landline 2")
            ),
            type = ContactType.DISASTER_RESPONSE
        ),

        EmergencyContact(
            id = "8",
            name = "PHIVOLCS",
            numbers = listOf(
                PhoneNumber("(02) 8426-1468", "Landline"),
                PhoneNumber("(02) 8929-9254", "Landline 2")
            ),
            type = ContactType.DISASTER_RESPONSE
        )
    )

    fun getLocalAuthorityContacts(city: String): List<EmergencyContact> {
        val normalized = city.lowercase().trim().removeSuffix(" city").trim()

        return when (normalized) {

            "quezon" -> listOf(
                EmergencyContact(
                    id = "local-qc-1",
                    name = "QCDRRMO",
                    numbers = listOf(
                        PhoneNumber("(02) 8988-4242", "Landline"),
                        PhoneNumber("0917-827-3764", "Globe"),
                        PhoneNumber("(02) 8988-4243", "Landline 2")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Quezon City"
                ),
                EmergencyContact(
                    id = "local-qc-2",
                    name = "Quezon City Police District",
                    numbers = listOf(
                        PhoneNumber("(02) 8922-9183", "Landline"),
                        PhoneNumber("(02) 8921-4067", "Landline 2")
                    ),
                    type = ContactType.POLICE,
                    region = "Quezon City"
                ),
                EmergencyContact(
                    id = "local-qc-3",
                    name = "QC Fire District",
                    numbers = listOf(
                        PhoneNumber("(02) 8922-0246", "Landline"),
                        PhoneNumber("(02) 8921-1985", "Landline 2")
                    ),
                    type = ContactType.FIRE,
                    region = "Quezon City"
                ),
                EmergencyContact(
                    id = "local-qc-4",
                    name = "Quezon City General Hospital",
                    numbers = listOf(
                        PhoneNumber("(02) 8988-4242", "Landline"),
                        PhoneNumber("(02) 8921-2266", "Landline 2")
                    ),
                    type = ContactType.MEDICAL,
                    region = "Quezon City"
                )
            )

            "manila" -> listOf(
                EmergencyContact(
                    id = "local-mnl-1",
                    name = "Manila DRRMO",
                    numbers = listOf(
                        PhoneNumber("(02) 8527-9365", "Landline"),
                        PhoneNumber("0998-961-3816", "Smart")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Manila"
                ),
                EmergencyContact(
                    id = "local-mnl-2",
                    name = "Manila Police District",
                    numbers = listOf(
                        PhoneNumber("(02) 8712-5885", "Landline"),
                        PhoneNumber("(02) 8527-4764", "Landline 2")
                    ),
                    type = ContactType.POLICE,
                    region = "Manila"
                ),
                EmergencyContact(
                    id = "local-mnl-3",
                    name = "Manila Fire District",
                    numbers = listOf(
                        PhoneNumber("(02) 8527-3697", "Landline")
                    ),
                    type = ContactType.FIRE,
                    region = "Manila"
                )
            )

            "makati" -> listOf(
                EmergencyContact(
                    id = "local-mkt-1",
                    name = "Makati DRRMO",
                    numbers = listOf(
                        PhoneNumber("(02) 8870-0285", "Landline"),
                        PhoneNumber("0917-899-6272", "Globe")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Makati"
                ),
                EmergencyContact(
                    id = "local-mkt-2",
                    name = "Makati Police Station",
                    numbers = listOf(
                        PhoneNumber("(02) 8899-8400", "Landline"),
                        PhoneNumber("(02) 8890-1414", "Landline 2")
                    ),
                    type = ContactType.POLICE,
                    region = "Makati"
                )
            )

            "pasig" -> listOf(
                EmergencyContact(
                    id = "local-psg-1",
                    name = "Pasig CDRRMO",
                    numbers = listOf(
                        PhoneNumber("(02) 8643-1111", "Landline"),
                        PhoneNumber("0917-892-8636", "Globe")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Pasig"
                ),
                EmergencyContact(
                    id = "local-psg-2",
                    name = "Pasig City Police Station",
                    numbers = listOf(
                        PhoneNumber("(02) 8643-1063", "Landline")
                    ),
                    type = ContactType.POLICE,
                    region = "Pasig"
                )
            )

            "caloocan" -> listOf(
                EmergencyContact(
                    id = "local-cal-1",
                    name = "Caloocan CDRRMO",
                    numbers = listOf(
                        PhoneNumber("(02) 8288-8811", "Landline"),
                        PhoneNumber("0917-893-1234", "Globe")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Caloocan"
                ),
                EmergencyContact(
                    id = "local-cal-2",
                    name = "Caloocan Police District",
                    numbers = listOf(
                        PhoneNumber("(02) 8367-0351", "Landline"),
                        PhoneNumber("(02) 8367-0352", "Landline 2")
                    ),
                    type = ContactType.POLICE,
                    region = "Caloocan"
                )
            )

            "valenzuela" -> listOf(
                EmergencyContact(
                    id = "local-val-1",
                    name = "Valenzuela CDRRMO",
                    numbers = listOf(
                        PhoneNumber("8352-5000", "Landline"),
                        PhoneNumber("8292-1405", "Landline 2"),
                        PhoneNumber("0919-009-4045", "Smart"),
                        PhoneNumber("0917-881-1639", "Globe")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Valenzuela"
                ),
                EmergencyContact(
                    id = "local-val-2",
                    name = "Valenzuela City Police Station",
                    numbers = listOf(
                        PhoneNumber("8352-4000", "Landline"),
                        PhoneNumber("0906-419-7676", "Globe"),
                        PhoneNumber("0998-598-7868", "Smart")
                    ),
                    type = ContactType.POLICE,
                    region = "Valenzuela"
                ),
                EmergencyContact(
                    id = "local-val-3",
                    name = "Valenzuela City Fire Station",
                    numbers = listOf(
                        PhoneNumber("8292-3519", "Landline")
                    ),
                    type = ContactType.FIRE,
                    region = "Valenzuela"
                ),
                EmergencyContact(
                    id = "local-val-4",
                    name = "Valenzuela City Emergency Hospital",
                    numbers = listOf(
                        PhoneNumber("8352-6000", "Landline")
                    ),
                    type = ContactType.MEDICAL,
                    region = "Valenzuela"
                ),
                EmergencyContact(
                    id = "local-val-5",
                    name = "Valenzuela Medical Center",
                    numbers = listOf(
                        PhoneNumber("8294-6711", "Landline")
                    ),
                    type = ContactType.MEDICAL,
                    region = "Valenzuela"
                )
            )

            else -> listOf(
                EmergencyContact(
                    id = "local-mmda-1",
                    name = "MMDA Operations",
                    numbers = listOf(
                        PhoneNumber("136", "Hotline"),
                        PhoneNumber("(02) 8882-4151", "Landline")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Metro Manila"
                ),
                EmergencyContact(
                    id = "local-mmda-2",
                    name = "MMDA Metrobase",
                    numbers = listOf(
                        PhoneNumber("0920-811-6565", "Globe"),
                        PhoneNumber("(02) 8882-0925", "Landline")
                    ),
                    type = ContactType.DISASTER_RESPONSE,
                    region = "Metro Manila"
                )
            )
        }
    }
}