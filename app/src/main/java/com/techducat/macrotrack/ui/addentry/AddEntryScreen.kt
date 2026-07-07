package com.techducat.macrotrack.ui.addentry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.techducat.macrotrack.model.MealType
import kotlin.math.roundToInt

@Composable
fun AddEntryScreen(
    onLogged: () -> Unit,
    viewModel: AddEntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.logged) {
        if (state.logged) onLogged()
    }

    if (state.isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        return
    }

    val food = state.food
    if (food == null) {
        Text("Food not found.", modifier = Modifier.padding(24.dp))
        return
    }

    val quantity = state.quantityGrams.toDoubleOrNull() ?: 0.0
    val scale = quantity / 100.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(food.name, style = MaterialTheme.typography.titleLarge)
        if (food.brand.isNotBlank()) {
            Text(food.brand, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.quantityGrams,
            onValueChange = viewModel::onQuantityChange,
            label = { Text("Quantity (grams)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        MealDropdown(selected = state.meal, onSelected = viewModel::onMealChange)
        Spacer(Modifier.height(16.dp))

        Text("This serving:", style = MaterialTheme.typography.titleMedium)
        Text("${(food.caloriesPer100g * scale).roundToInt()} kcal")
        Text(
            "Protein ${(food.proteinPer100gGrams * scale).roundToInt()}g  •  " +
                "Carbs ${(food.carbsPer100gGrams * scale).roundToInt()}g  •  " +
                "Fat ${(food.fatPer100gGrams * scale).roundToInt()}g"
        )
        Spacer(Modifier.height(24.dp))

        Button(onClick = viewModel::logEntry, modifier = Modifier.fillMaxWidth()) {
            Text("Log to diary")
        }
    }
}

@Composable
private fun MealDropdown(selected: MealType, onSelected: (MealType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Meal") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().then(Modifier)
        )
        androidx.compose.material3.ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MealType.entries.forEach { meal ->
                DropdownMenuItem(
                    text = { Text(meal.label) },
                    onClick = {
                        onSelected(meal)
                        expanded = false
                    }
                )
            }
        }
    }
}
