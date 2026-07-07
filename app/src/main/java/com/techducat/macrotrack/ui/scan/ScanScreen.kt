package com.techducat.macrotrack.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.techducat.macrotrack.R
import com.techducat.macrotrack.data.db.FoodEntity
import com.techducat.macrotrack.scanner.BarcodeAnalyzer
import java.util.concurrent.Executors

@Composable
fun ScanScreen(
    onFoodConfirmed: (FoodEntity) -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(onBarcodeDetected = viewModel::onBarcodeDetected)
        } else {
            Column(modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(stringResource(R.string.scan_permission_rationale))
            }
        }

        when (val s = state) {
            is ScanUiState.Looking -> ScanOverlay { CircularProgressIndicator() }
            is ScanUiState.Found -> ScanOverlay {
                Column {
                    Text(s.food.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.scan_result_calories, s.food.caloriesPer100g.toInt()))
                    Button(onClick = { onFoodConfirmed(s.food) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.action_add_to_diary))
                    }
                    Button(onClick = viewModel::resetToScanning, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.action_scan_again))
                    }
                }
            }
            is ScanUiState.NotFound -> ScanOverlay {
                Column {
                    Text(stringResource(R.string.scan_not_found, s.barcode))
                    Button(onClick = viewModel::resetToScanning, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.action_scan_again))
                    }
                }
            }
            ScanUiState.Scanning -> Unit
        }
    }
}

@Composable
private fun ScanOverlay(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.0f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) { content() }
            }
        }
    }
}

@Composable
private fun CameraPreview(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, BarcodeAnalyzer(onBarcodeDetected)) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}
