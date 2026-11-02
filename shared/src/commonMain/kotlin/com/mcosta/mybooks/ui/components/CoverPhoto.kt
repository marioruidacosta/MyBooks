package com.mcosta.mybooks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.mcosta.mybooks.ui.LocalCoverStore
import com.mcosta.mybooks.ui.LocalPlatformServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Returns a function that opens the platform camera to photograph a cover. On success the photo is
 * shrunk, stored, and [onPhoto] receives its `cover:` reference; [onError] gets a message otherwise.
 */
@Composable
fun rememberCoverPhotoTaker(onPhoto: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val services = LocalPlatformServices.current
    val store = LocalCoverStore.current
    val scope = rememberCoroutineScope()
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val currentOnError by rememberUpdatedState(onError)
    return {
        scope.launch {
            try {
                val photo = services.camera.takePhoto() ?: return@launch
                val ref = withContext(Dispatchers.Default) { store.save(services.shrinker.shrink(photo)) }
                currentOnPhoto(ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentOnError("Couldn't use the photo: ${e.message ?: "unknown error"}")
            }
        }
    }
}
