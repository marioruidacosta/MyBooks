package com.mcosta.mybooks.scan

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.mcosta.mybooks.domain.Isbn

sealed interface ScanOutcome {
    data class Isbn(val isbn: String) : ScanOutcome
    data object NotAnIsbn : ScanOutcome
    data object Cancelled : ScanOutcome
    data class Failed(val message: String) : ScanOutcome
}

/**
 * Scans a book barcode with Google's code scanner (Play services UI; no camera permission needed).
 * [context] should be the Activity.
 */
class IsbnScanner(private val context: Context) {
    fun scan(onResult: (ScanOutcome) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val isbn = barcode.rawValue?.let { Isbn.normalize(it) }
                onResult(if (isbn != null) ScanOutcome.Isbn(isbn) else ScanOutcome.NotAnIsbn)
            }
            .addOnCanceledListener { onResult(ScanOutcome.Cancelled) }
            .addOnFailureListener { e -> onResult(ScanOutcome.Failed(e.message ?: "Scanner unavailable")) }
    }
}
