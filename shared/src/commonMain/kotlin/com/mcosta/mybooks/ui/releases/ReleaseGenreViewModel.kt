package com.mcosta.mybooks.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.releases.MonthBooks
import com.mcosta.mybooks.data.releases.ReleaseBook
import com.mcosta.mybooks.data.releases.ReleaseFilter
import com.mcosta.mybooks.data.releases.matches
import com.mcosta.mybooks.data.releases.ReleaseGenre
import com.mcosta.mybooks.data.releases.ReleasesRepository
import com.mcosta.mybooks.data.releases.ReleasesUnavailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class GenreBooksState(
    /** The books by month, newest first; one group for a single month, only months with books for all months. */
    val groups: List<MonthBooks> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Set when the saved copy is shown because the download failed. */
    val savedAt: Instant? = null,
    /** Whether the month is over, from the saved index (null until known; single month only). */
    val monthComplete: Boolean? = null,
    /** Months that couldn't be loaded (all months only). */
    val missing: List<String> = emptyList(),
) {
    val books: List<ReleaseBook> get() = groups.flatMap { it.books }
}

/** One month's (or, with a null [month], every month's) new books of one genre, author or publisher, A–Z. */
class ReleaseGenreViewModel(
    private val repo: ReleasesRepository,
    private val month: String?,
    private val filter: ReleaseFilter,
) : ViewModel() {
    constructor(repo: ReleasesRepository, month: String, genre: ReleaseGenre) : this(repo, month, ReleaseFilter.Genre(genre))

    private val _state = MutableStateFlow(GenreBooksState())
    val state: StateFlow<GenreBooksState> = _state.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (month == null) loadAll() else loadMonth(month)
            } catch (e: ReleasesUnavailable) {
                _state.update { it.copy(error = LOAD_ERROR) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun loadMonth(month: String) {
        // "(so far)" follows the month list, like the month picker, not the phone's clock.
        val complete = repo.savedMonth(month)?.complete
        _state.update { it.copy(monthComplete = complete) }
        val cached = repo.books(month)
        val books = cached.value.filter { b -> b.matches(filter) }
        _state.update {
            it.copy(groups = listOf(MonthBooks(month, complete ?: false, books)), savedAt = cached.savedAt.takeIf { cached.offline })
        }
    }

    private suspend fun loadAll() {
        val all = repo.allMonths()
        val groups = all.months.map { m -> m.copy(books = m.books.filter { b -> b.matches(filter) }) }.filter { it.books.isNotEmpty() }
        _state.update { it.copy(groups = groups, savedAt = all.savedAt, missing = all.missing) }
    }
}
