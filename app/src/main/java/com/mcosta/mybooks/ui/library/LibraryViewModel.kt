package com.mcosta.mybooks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryState(
    val tab: ReadingStatus? = null,
    val query: String = "",
    val books: List<Book> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repo: BookRepository) : ViewModel() {
    private val tab = MutableStateFlow<ReadingStatus?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<LibraryState> = combine(tab, query) { t, q -> t to q }
        .flatMapLatest { (t, q) -> repo.observeBooks(t, q).map { LibraryState(t, q, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun selectTab(status: ReadingStatus?) {
        tab.value = status
    }

    fun setQuery(q: String) {
        query.value = q
    }

    suspend fun findByIsbn(isbn: String): Book? = repo.findByIsbn(isbn)
}
