package com.mcosta.mybooks.platform

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.mcosta.mybooks.domain.Isbn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Scans a book barcode with Google's code scanner (Play services UI; no camera permission needed).
 * [context] should be the Activity.
 */
class AndroidIsbnScanner(private val context: Context) : IsbnScanner {
    override suspend fun scan(): ScanOutcome = suspendCancellableCoroutine { cont ->
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val isbn = barcode.rawValue?.let { Isbn.normalize(it) }
                cont.resume(if (isbn != null) ScanOutcome.Isbn(isbn) else ScanOutcome.NotAnIsbn)
            }
            .addOnCanceledListener { cont.resume(ScanOutcome.Cancelled) }
            .addOnFailureListener { e -> cont.resume(ScanOutcome.Failed(e.message ?: "Scanner unavailable")) }
    }
}
