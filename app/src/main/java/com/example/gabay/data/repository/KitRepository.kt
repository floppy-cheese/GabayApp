package com.example.gabay.data.repository

import android.content.Context
import com.example.gabay.data.models.EmergencyKitItem
import com.example.gabay.data.models.KitCategory

class KitRepository(private val context: Context) {

    fun getBasicKitItems(): List<EmergencyKitItem> {
        val items = listOf(
            EmergencyKitItem(
                id = "basic_01",
                name = "Water",
                description = "One gallon per person per day for several days, for drinking and sanitation",
                quantityFormula = "households * 3",
                isBasic = true,
                category = KitCategory.WATER_FOOD,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_02",
                name = "Food",
                description = "At least a several-day supply of non-perishable food",
                quantityFormula = "3-day supply",
                isBasic = true,
                category = KitCategory.WATER_FOOD,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_03",
                name = "Battery-powered or Hand Crank Radio",
                description = "For emergency broadcasts",
                quantityFormula = "1",
                isBasic = true,
                category = KitCategory.COMMUNICATION,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_04",
                name = "Flashlight",
                description = "With extra batteries",
                quantityFormula = "1 per person",
                isBasic = true,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_05",
                name = "First Aid Kit",
                description = "Basic medical supplies",
                quantityFormula = "1",
                isBasic = true,
                category = KitCategory.MEDICAL,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_06",
                name = "Extra Batteries",
                description = "Various sizes for devices",
                quantityFormula = "Multiple sets",
                isBasic = true,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_07",
                name = "Whistle",
                description = "To signal for help",
                quantityFormula = "1 per person",
                isBasic = true,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_08",
                name = "Dust Mask",
                description = "To help filter contaminated air",
                quantityFormula = "1 per person",
                isBasic = true,
                category = KitCategory.MEDICAL,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_09",
                name = "Plastic Sheeting, Scissors and Duct Tape",
                description = "To shelter in place",
                quantityFormula = "1 set",
                isBasic = true,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_10",
                name = "Moist Towelettes, Garbage Bags and Plastic Ties",
                description = "For personal sanitation",
                quantityFormula = "Multiple packs",
                isBasic = true,
                category = KitCategory.SANITATION,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_11",
                name = "Wrench or Pliers",
                description = "To turn off utilities",
                quantityFormula = "1",
                isBasic = true,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_12",
                name = "Manual Can Opener",
                description = "For food",
                quantityFormula = "1",
                isBasic = true,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_13",
                name = "Local Maps",
                description = "Printed maps of your area",
                quantityFormula = "1",
                isBasic = true,
                category = KitCategory.COMMUNICATION,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "basic_14",
                name = "Cell Phone with Chargers and Backup Battery",
                description = "For emergency communication",
                quantityFormula = "1 per person",
                isBasic = true,
                category = KitCategory.COMMUNICATION,
                isChecked = false,
                calculatedQuantity = ""
            )
        )

        android.util.Log.d("KitRepository", "Returning ${items.size} basic items")
        items.forEachIndexed { index, item ->
            android.util.Log.d("KitRepository", "${index + 1}. ${item.name}")
        }
        return items
    }

    fun getAdditionalKitItems(): List<EmergencyKitItem> {
        val items = listOf(
            EmergencyKitItem(
                id = "additional_01",
                name = "Soap, Hand Sanitizer and Disinfecting Wipes",
                description = "To disinfect surfaces",
                quantityFormula = "Multiple",
                isBasic = false,
                category = KitCategory.SANITATION,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_02",
                name = "Prescription Medications",
                description = "Several days supply",
                quantityFormula = "As needed",
                isBasic = false,
                category = KitCategory.MEDICAL,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_03",
                name = "Non-prescription Medications",
                description = "Pain relievers, anti-diarrhea medication, antacids or laxatives",
                quantityFormula = "As needed",
                isBasic = false,
                category = KitCategory.MEDICAL,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_04",
                name = "Prescription Eyeglasses and Contact Lens Solution",
                description = "Spare pair",
                quantityFormula = "As needed",
                isBasic = false,
                category = KitCategory.MEDICAL,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_05",
                name = "Infant Formula, Bottles, Diapers, Wipes and Diaper Rash Cream",
                description = "For infants",
                quantityFormula = "As needed",
                isBasic = false,
                category = KitCategory.INFANT,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_06",
                name = "Pet Food and Extra Water",
                description = "For your pet",
                quantityFormula = "As needed",
                isBasic = false,
                category = KitCategory.PET,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_07",
                name = "Cash or Traveler's Checks",
                description = "Small bills",
                quantityFormula = "Sufficient",
                isBasic = false,
                category = KitCategory.DOCUMENTS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_08",
                name = "Important Family Documents",
                description = "Copies of insurance policies, identification and bank account records",
                quantityFormula = "1 set",
                isBasic = false,
                category = KitCategory.DOCUMENTS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_09",
                name = "Sleeping Bag or Warm Blanket",
                description = "For each person",
                quantityFormula = "1 per person",
                isBasic = false,
                category = KitCategory.CLOTHING,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_10",
                name = "Complete Change of Clothing",
                description = "Appropriate for your climate and sturdy shoes",
                quantityFormula = "1 set per person",
                isBasic = false,
                category = KitCategory.CLOTHING,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_11",
                name = "Fire Extinguisher",
                description = "Multipurpose",
                quantityFormula = "1",
                isBasic = false,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_12",
                name = "Matches in Waterproof Container",
                description = "For emergencies",
                quantityFormula = "Multiple",
                isBasic = false,
                category = KitCategory.TOOLS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_13",
                name = "Feminine Supplies and Personal Hygiene Items",
                description = "For sanitation",
                quantityFormula = "As needed",
                isBasic = false,
                category = KitCategory.SANITATION,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_14",
                name = "Mess Kits, Paper Cups, Plates, Paper Towels and Plastic Utensils",
                description = "For eating",
                quantityFormula = "1 set per person",
                isBasic = false,
                category = KitCategory.WATER_FOOD,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_15",
                name = "Paper and Pencil",
                description = "For notes and communication",
                quantityFormula = "1 set",
                isBasic = false,
                category = KitCategory.DOCUMENTS,
                isChecked = false,
                calculatedQuantity = ""
            ),
            EmergencyKitItem(
                id = "additional_16",
                name = "Books, Games, Puzzles or Other Activities",
                description = "For children",
                quantityFormula = "As needed",
                isBasic = false,
                category = KitCategory.DOCUMENTS,
                isChecked = false,
                calculatedQuantity = ""
            )
        )

        android.util.Log.d("KitRepository", "Returning ${items.size} additional items")
        return items
    }

    fun calculateQuantity(householdSize: Int, formula: String): String {
        return when {
            formula.contains("per person") -> "$householdSize"
            formula.contains("households") -> {
                val mult = formula.split("*").getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                "${householdSize * mult} gallons"
            }
            formula.contains("3-day") ->
                "3-day supply for $householdSize ${if(householdSize > 1) "people" else "person"}"
            else -> formula
        }
    }
}