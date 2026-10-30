package com.mcosta.mybooks.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Reads and writes text documents picked by the user. */
interface IoPort {
    suspend fun write(uri: Uri, text: String)
    suspend fun read(uri: Uri): String
}

class ContentResolverIo(private val resolver: ContentResolver) : IoPort {
    override suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException("Can't open $uri")
        out.use { it.write(text.toByteArray()) }
    }

    override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("Can't open $uri")
        input.use { it.readBytes().decodeToString() }
    }
}

class SettingsViewModel(private val repo: BookRepository, private val io: IoPort) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun export(uri: Uri) = run("Export failed") {
        val json = repo.exportJson()
        io.write(uri, json)
        val count = repo.bookCount()
        "Library exported ($count ${if (count == 1) "book" else "books"})"
    }

    fun import(uri: Uri) = run("Import failed") {
        val s = repo.importJson(io.read(uri))
        "Imported: ${s.added} added, ${s.updated} updated, ${s.failed} failed"
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun run(failurePrefix: String, action: suspend () -> String) {
        viewModelScope.launch {
            _message.value = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "$failurePrefix: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
}
