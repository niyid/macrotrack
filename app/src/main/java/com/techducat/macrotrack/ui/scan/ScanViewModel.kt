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
    data class Unavailable(val barcode: String) : ScanUiState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var lookupInFlight = false
    private var lastBarcode: String? = null

    fun onBarcodeDetected(barcode: String) {
        if (lookupInFlight) return
        lookupInFlight = true
        lastBarcode = barcode
        _uiState.value = ScanUiState.Looking
        viewModelScope.launch {
            _uiState.value = when (val result = foodRepository.lookupBarcode(barcode)) {
                is FoodRepository.BarcodeLookup.Found -> ScanUiState.Found(result.food)
                is FoodRepository.BarcodeLookup.NotFound -> ScanUiState.NotFound(barcode)
                is FoodRepository.BarcodeLookup.Unavailable -> ScanUiState.Unavailable(barcode)
            }
            lookupInFlight = false
        }
    }

    /** Re-runs the lookup for the barcode that just failed with [ScanUiState.Unavailable]. */
    fun retryLastLookup() {
        lastBarcode?.let { onBarcodeDetected(it) }
    }

    fun resetToScanning() {
        _uiState.value = ScanUiState.Scanning
    }
}
