package com.techducat.macrotrack

import com.techducat.macrotrack.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Test

class MealTypeTest {

    @Test
    fun `fromStorage returns matching enum`() {
        assertEquals(MealType.BREAKFAST, MealType.fromStorage("BREAKFAST"))
        assertEquals(MealType.DINNER, MealType.fromStorage("DINNER"))
    }

    @Test
    fun `fromStorage falls back to SNACK for unknown values`() {
        assertEquals(MealType.SNACK, MealType.fromStorage("GARBAGE"))
    }
}
