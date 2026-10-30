package com.mcosta.mybooks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Book detail: status, rating and notes edits are saved immediately; the notes draft is also saved
 * when the screen is left ([onCleared]) using [saveScope], which outlives the ViewModel.
 */
class BookDetailViewModel(
    private val repo: BookRepository,
    private val id: Long,
    private val saveScope: CoroutineScope,
) : ViewModel() {
    private val _notesDraft = MutableStateFlow("")
    val notesDraft: StateFlow<String> = _notesDraft.asStateFlow()

    /** Stored notes the draft was last in sync with; null until the book has loaded. */
    private var syncedNotes: String? = null
    private val hasUnsavedNotes get() = syncedNotes != null && _notesDraft.value != syncedNotes

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    val book: StateFlow<Book?> = repo.observeBook(id)
        .onEach { b ->
            // Follow notes changed elsewhere (e.g. the Edit screen) unless the user has unsaved typing.
            if (b != null && b.notes != syncedNotes) {
                if (!hasUnsavedNotes) _notesDraft.value = b.notes
                syncedNotes = b.notes
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setStatus(status: ReadingStatus) {
        viewModelScope.launch { repo.setStatus(id, status) }
    }

    fun rate(rating: Int?) {
        viewModelScope.launch { repo.setRating(id, rating) }
    }

    fun editNotes(text: String) {
        _notesDraft.value = text
    }

    fun saveNotes() {
        if (_deleted.value || !hasUnsavedNotes) return
        val draft = _notesDraft.value
        syncedNotes = draft
        saveScope.launch { repo.setNotes(id, draft) }
    }

    fun delete() {
        _deleted.value = true
        viewModelScope.launch { repo.delete(id) }
    }

    override fun onCleared() {
        saveNotes()
    }
}
