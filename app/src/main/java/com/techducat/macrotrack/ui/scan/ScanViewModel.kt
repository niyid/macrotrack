package com.techducat.macrotrack.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techducat.macrotrack.data.db.FoodEntity
import com.techducat.macrotrack.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanUiState {
    data object Scanning : ScanUiState
    data object Looking : ScanUiState
    data class Found(val food: FoodEntity) : ScanUiState
    data class NotFound(val barcode: String) : ScanUiState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var lookupInFlight = false

    fun onBarcodeDetected(barcode: String) {
        if (lookupInFlight) return
        lookupInFlight = true
        _uiState.value = ScanUiState.Looking
        viewModelScope.launch {
            val food = foodRepository.lookupBarcode(barcode)
            _uiState.value = if (food != null) ScanUiState.Found(food) else ScanUiState.NotFound(barcode)
            lookupInFlight = false
        }
    }

    fun resetToScanning() {
        _uiState.value = ScanUiState.Scanning
    }
}
