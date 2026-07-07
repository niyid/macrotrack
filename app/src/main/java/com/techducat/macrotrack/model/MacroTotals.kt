package com.techducat.macrotrack.model

/** Domain-level macro snapshot, used for both "today's totals" and "goals". */
data class MacroTotals(
    val calories: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0
)

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun fromStorage(value: String): MealType =
            entries.firstOrNull { it.name == value } ?: SNACK
    }
}
