package com.mcosta.mybooks.platform

import com.mcosta.mybooks.domain.Isbn
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import kotlin.coroutines.resume

/**
 * Implemented in Swift by the iOS app (iosApp/MyBooks/NativeServices.swift): VisionKit's barcode
 * scanner is Swift-only, and UIKit pickers are simplest there. Callbacks report (result, error);
 * both null means the user cancelled.
 */
interface IosNativeServices {
    fun scanBarcode(completion: (String?, String?) -> Unit)
    fun takePhoto(completion: (NSData?, String?) -> Unit)
    /** completion("saved", null) when saved. */
    fun saveDocument(name: String, data: NSData, completion: (String?, String?) -> Unit)
    fun openDocument(types: List<String>, completion: (NSData?, String?) -> Unit)
}

private class NativeCallException(message: String) : Exception(message)

class IosIsbnScanner(private val native: IosNativeServices) : IsbnScanner {
    override suspend fun scan(): ScanOutcome = suspendCancellableCoroutine { cont ->
        native.scanBarcode { code, error ->
            cont.resume(
                when {
                    error != null -> ScanOutcome.Failed(error)
                    code == null -> ScanOutcome.Cancelled
                    else -> Isbn.normalize(code)?.let { ScanOutcome.Isbn(it) } ?: ScanOutcome.NotAnIsbn
                }
            )
        }
    }
}

class IosCoverCamera(private val native: IosNativeServices) : CoverCamera {
    override suspend fun takePhoto(): ByteArray? = suspendCancellableCoroutine { cont ->
        native.takePhoto { data, error ->
            cont.resumeWith(if (error != null) Result.failure(NativeCallException(error)) else Result.success(data?.toByteArray()))
        }
    }
}

class IosDocumentPicker(private val native: IosNativeServices) : DocumentPicker {
    override suspend fun save(suggestedName: String, mime: String, bytes: ByteArray): Boolean = suspendCancellableCoroutine { cont ->
        native.saveDocument(suggestedName, bytes.toNSData()) { saved, error ->
            cont.resumeWith(if (error != null) Result.failure(NativeCallException(error)) else Result.success(saved != null))
        }
    }

    override suspend fun open(mimeTypes: List<String>): ByteArray? = suspendCancellableCoroutine { cont ->
        native.openDocument(mimeTypes) { data, error ->
            cont.resumeWith(if (error != null) Result.failure(NativeCallException(error)) else Result.success(data?.toByteArray()))
        }
    }
}

fun iosPlatformServices(native: IosNativeServices) = PlatformServices(
    scanner = IosIsbnScanner(native),
    camera = IosCoverCamera(native),
    shrinker = IosPhotoShrinker,
    documents = IosDocumentPicker(native),
)
