package com.techducat.macrotrack.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.techducat.macrotrack.R
import com.techducat.macrotrack.model.MacroTotals

@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()

    var calories by remember { mutableStateOf(goals.calories.toInt().toString()) }
    var protein by remember { mutableStateOf(goals.proteinGrams.toInt().toString()) }
    var carbs by remember { mutableStateOf(goals.carbsGrams.toInt().toString()) }
    var fat by remember { mutableStateOf(goals.fatGrams.toInt().toString()) }

    LaunchedEffect(goals) {
        calories = goals.calories.toInt().toString()
        protein = goals.proteinGrams.toInt().toString()
        carbs = goals.carbsGrams.toInt().toString()
        fat = goals.fatGrams.toInt().toString()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = calories, onValueChange = { calories = it },
            label = { Text(stringResource(R.string.goals_calories_label)) }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = protein, onValueChange = { protein = it },
            label = { Text(stringResource(R.string.goals_protein_label)) }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = carbs, onValueChange = { carbs = it },
            label = { Text(stringResource(R.string.goals_carbs_label)) }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = fat, onValueChange = { fat = it },
            label = { Text(stringResource(R.string.goals_fat_label)) }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.save(
                    MacroTotals(
                        calories = calories.toDoubleOrNull() ?: goals.calories,
                        proteinGrams = protein.toDoubleOrNull() ?: goals.proteinGrams,
                        carbsGrams = carbs.toDoubleOrNull() ?: goals.carbsGrams,
                        fatGrams = fat.toDoubleOrNull() ?: goals.fatGrams
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}
