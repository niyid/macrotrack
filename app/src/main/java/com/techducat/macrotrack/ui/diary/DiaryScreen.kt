package com.techducat.macrotrack.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.techducat.macrotrack.data.db.DiaryEntryEntity
import com.techducat.macrotrack.model.MealType
import com.techducat.macrotrack.ui.theme.CarbsColor
import com.techducat.macrotrack.ui.theme.FatColor
import com.techducat.macrotrack.ui.theme.ProteinColor
import kotlin.math.roundToInt

@Composable
fun DiaryScreen(viewModel: DiaryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CaloriesSummaryCard(state) }
        item { MacroBar("Protein", state.totals.proteinGrams, state.goals.proteinGrams, ProteinColor) }
        item { MacroBar("Carbs", state.totals.carbsGrams, state.goals.carbsGrams, CarbsColor) }
        item { MacroBar("Fat", state.totals.fatGrams, state.goals.fatGrams, FatColor) }

        MealType.entries.forEach { meal ->
            val entries = state.entries.filter { MealType.fromStorage(it.mealType) == meal }
            if (entries.isNotEmpty()) {
                item {
                    Text(
                        text = meal.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    DiaryEntryRow(entry) { viewModel.deleteEntry(entry) }
                }
            }
        }

        if (state.entries.isEmpty()) {
            item {
                Text(
                    text = "Nothing logged yet today. Tap Search or Scan to add a food.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CaloriesSummaryCard(state: DiaryUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Calories", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.totals.calories.roundToInt()} / ${state.goals.calories.roundToInt()} kcal",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            val progress = if (state.goals.calories > 0)
                (state.totals.calories / state.goals.calories).toFloat().coerceIn(0f, 1f)
            else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
    }
}

@Composable
private fun MacroBar(label: String, current: Double, goal: Double, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${current.roundToInt()}g / ${goal.roundToInt()}g", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            val progress = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}

@Composable
private fun DiaryEntryRow(entry: DiaryEntryEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(entry.foodName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${entry.quantityGrams.roundToInt()}g • ${entry.calories.roundToInt()} kcal " +
                        "• P${entry.proteinGrams.roundToInt()} C${entry.carbsGrams.roundToInt()} F${entry.fatGrams.roundToInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
            }
        }
    }
}
