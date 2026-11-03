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
    val sort: SortChoice = SortChoice(),
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
    /** Each tab's own sort (All is the null tab), as saved. */
    private val sorts = MutableStateFlow(TABS.associateWith { SortChoice.decode(prefs?.get(key(it))) ?: SortChoice() })

    val state: StateFlow<LibraryState> = combine(tab, query, sorts) { t, q, s -> Triple(t, q, s.getValue(t)) }
        .flatMapLatest { (t, q, s) -> repo.observeBooks(t, q).map { LibraryState(t, q, it.sortedFor(s), s) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun selectTab(status: ReadingStatus?) {
        tab.value = status
    }

    fun setQuery(q: String) {
        query.value = q
    }

    /** Sorts this tab by [order], in its usual direction: A to Z, or highest score first. */
    fun setSort(order: LibrarySort) {
        if (order == LibrarySort.SCORE && tab.value != ReadingStatus.READ) return
        save(SortChoice(order, descending = order == LibrarySort.SCORE))
    }

    fun setDescending(descending: Boolean) = save(sorts.value.getValue(tab.value).copy(descending = descending))

    suspend fun findByIsbn(isbn: String): Book? = repo.findByIsbn(isbn)

    private fun save(choice: SortChoice) {
        val t = tab.value
        sorts.value += t to choice
        prefs?.set(key(t), choice.encode())
    }

    private companion object {
        val TABS = listOf(null) + ReadingStatus.entries
        fun key(tab: ReadingStatus?) = "library.sort.${tab?.name ?: "ALL"}"
    }
}
