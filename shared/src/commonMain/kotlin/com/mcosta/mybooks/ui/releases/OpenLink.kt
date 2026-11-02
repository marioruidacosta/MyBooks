package com.mcosta.mybooks.ui.releases

import androidx.compose.ui.platform.UriHandler

/** Opens [url]; false when nothing on the phone can open it (opening would otherwise crash the app). */
internal fun UriHandler.tryOpen(url: String): Boolean = try {
    openUri(url)
    true
} catch (e: Exception) {
    false
}
