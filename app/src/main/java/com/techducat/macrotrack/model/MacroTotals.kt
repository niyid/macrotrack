package com.techducat.macrotrack.model

import androidx.annotation.StringRes
import com.techducat.macrotrack.R

/** Domain-level macro snapshot, used for both "today's totals" and "goals". */
data class MacroTotals(
    val calories: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0
)

/**
 * [labelRes] points at strings.xml rather than holding a raw display string,
 * so meal names are localizable/externalized like every other user-facing
 * string in the app. Callers resolve it with `stringResource(meal.labelRes)`.
 */
enum class MealType(@StringRes val labelRes: Int) {
    BREAKFAST(R.string.meal_breakfast),
    LUNCH(R.string.meal_lunch),
    DINNER(R.string.meal_dinner),
    SNACK(R.string.meal_snack);

    companion object {
        fun fromStorage(value: String): MealType =
            entries.firstOrNull { it.name == value } ?: SNACK
    }
}
