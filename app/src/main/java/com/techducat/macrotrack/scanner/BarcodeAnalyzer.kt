package com.techducat.macrotrack.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber

/**
 * BarcodeAnalyzer — feeds camera frames to ML Kit's on-device barcode scanner.
 *
 * Fully on-device: no frame or image data ever leaves the phone. Only once a
 * barcode number is decoded does anything go over the network (the Open Food
 * Facts lookup for that single barcode string, in FoodRepository).
 *
 * A simple "last seen barcode" debounce avoids firing [onBarcodeDetected]
 * dozens of times per second while the user holds the camera steady.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128
            )
            .build()
    )

    private var lastBarcode: String? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (value != null && value != lastBarcode) {
                    lastBarcode = value
                    onBarcodeDetected(value)
                }
            }
            .addOnFailureListener { e -> Timber.w(e, "Barcode scan failed") }
            .addOnCompleteListener { imageProxy.close() }
    }
}
