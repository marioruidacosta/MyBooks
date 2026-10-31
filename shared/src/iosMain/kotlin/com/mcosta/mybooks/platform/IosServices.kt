package com.mcosta.mybooks.platform

/** Stage 2 placeholders: the iPhone scanner, camera and file pickers arrive in the next update. */
private const val COMING_SOON = "Coming in the next update"

class IosIsbnScanner : IsbnScanner {
    override suspend fun scan(): ScanOutcome = ScanOutcome.Failed("Barcode scanning: $COMING_SOON")
}

class IosCoverCamera : CoverCamera {
    override suspend fun takePhoto(): ByteArray? = throw IllegalStateException(COMING_SOON)
}

class IosDocumentPicker : DocumentPicker {
    override suspend fun save(suggestedName: String, mime: String, bytes: ByteArray): Boolean =
        throw IllegalStateException(COMING_SOON)

    override suspend fun open(mimeTypes: List<String>): ByteArray? = throw IllegalStateException(COMING_SOON)
}

fun iosPlatformServices() = PlatformServices(
    scanner = IosIsbnScanner(),
    camera = IosCoverCamera(),
    shrinker = IosPhotoShrinker,
    documents = IosDocumentPicker(),
)
