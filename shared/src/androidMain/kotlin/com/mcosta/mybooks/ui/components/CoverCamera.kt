package com.mcosta.mybooks.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.mcosta.mybooks.data.covers.CoverStore
import com.mcosta.mybooks.data.covers.AndroidPhotoShrinker
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The app's cover photo store (files/covers). */
fun coverStoreFor(context: Context) = CoverStore(FileSystem.SYSTEM, context.filesDir.toOkioPath() / "covers")

/**
 * Returns a function that opens the phone's camera app to photograph a cover. On success the photo is
 * shrunk, stored, and [onPhoto] receives its `cover:` reference; [onError] gets a message otherwise.
 * No CAMERA permission is needed: the camera app takes the picture.
 */
@Composable
fun rememberCoverPhotoTaker(onPhoto: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val currentOnError by rememberUpdatedState(onError)
    // Kept across activity recreation (the camera app may rotate the screen).
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPath?.let(::File) ?: return@rememberLauncherForActivityResult
        pendingPath = null
        if (!saved) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val ref = withContext(Dispatchers.IO) { coverStoreFor(context).save(AndroidPhotoShrinker.shrink(file.readBytes())) }
                currentOnPhoto(ref)
            } catch (e: Exception) {
                currentOnError("Couldn't use the photo: ${e.message ?: "unknown error"}")
            } finally {
                withContext(Dispatchers.IO) { file.delete() }
            }
        }
    }

    return {
        val file = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "cover-${System.currentTimeMillis()}.jpg") }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingPath = file.path
        try {
            launcher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingPath = null
            currentOnError("No camera app found")
        }
    }
}
