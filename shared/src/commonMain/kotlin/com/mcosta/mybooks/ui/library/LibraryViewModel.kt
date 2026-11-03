package com.mcosta.mybooks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.Preferences
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
    val sort: LibrarySort = LibrarySort.TITLE,
) {
    /** The orders offered on this tab: Score only for Read books. */
    val sorts: List<LibrarySort> get() = if (tab == ReadingStatus.READ) LibrarySort.entries else LibrarySort.entries - LibrarySort.SCORE
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repo: BookRepository,
    /** Remembers the sort orders between launches; null keeps them for this session only. */
    private val prefs: Preferences? = null,
) : ViewModel() {
    private val tab = MutableStateFlow<ReadingStatus?>(null)
    private val query = MutableStateFlow("")
    /** One order for All, Reading, To read and To buy; Read has its own (it alone offers Score). */
    private val sort = MutableStateFlow(saved(SORT_KEY))
    private val readSort = MutableStateFlow(saved(READ_SORT_KEY))

    val state: StateFlow<LibraryState> = combine(tab, query, sort, readSort) { t, q, s, r ->
        Triple(t, q, if (t == ReadingStatus.READ) r else s)
    }
        .flatMapLatest { (t, q, s) -> repo.observeBooks(t, q).map { LibraryState(t, q, it.sortedFor(s), s) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun selectTab(status: ReadingStatus?) {
        tab.value = status
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun setSort(order: LibrarySort) {
        if (tab.value == ReadingStatus.READ) {
            readSort.value = order
            prefs?.set(READ_SORT_KEY, order.name)
        } else if (order != LibrarySort.SCORE) {
            sort.value = order
            prefs?.set(SORT_KEY, order.name)
        }
    }

    suspend fun findByIsbn(isbn: String): Book? = repo.findByIsbn(isbn)

    private fun saved(key: String): LibrarySort =
        prefs?.get(key)?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } } ?: LibrarySort.TITLE

    private companion object {
        const val SORT_KEY = "library.sort"
        const val READ_SORT_KEY = "library.sort.read"
    }
}
