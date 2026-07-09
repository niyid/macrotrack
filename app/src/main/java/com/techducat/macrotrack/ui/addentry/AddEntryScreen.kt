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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.techducat.macrotrack.R
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
        Text(stringResource(R.string.add_entry_food_not_found), modifier = Modifier.padding(24.dp))
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
            label = { Text(stringResource(R.string.add_entry_quantity_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        MealDropdown(selected = state.meal, onSelected = viewModel::onMealChange)
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.add_entry_serving_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.add_entry_serving_calories, (food.caloriesPer100g * scale).roundToInt()))
        Text(
            stringResource(
                R.string.add_entry_serving_macros,
                (food.proteinPer100gGrams * scale).roundToInt(),
                (food.carbsPer100gGrams * scale).roundToInt(),
                (food.fatPer100gGrams * scale).roundToInt()
            )
        )
        Spacer(Modifier.height(24.dp))

        Button(onClick = viewModel::logEntry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_log_to_diary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealDropdown(selected: MealType, onSelected: (MealType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.add_entry_meal_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // Required by ExposedDropdownMenuBox to size/position the menu against this
            // field. Without it the dropdown doesn't anchor correctly under the text field.
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MealType.entries.forEach { meal ->
                DropdownMenuItem(
                    text = { Text(stringResource(meal.labelRes)) },
                    onClick = {
                        onSelected(meal)
                        expanded = false
                    }
                )
            }
        }
    }
}
