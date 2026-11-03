package com.mcosta.mybooks.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.releases.NameCount
import com.mcosta.mybooks.data.releases.ReleasesRepository
import com.mcosta.mybooks.data.releases.ReleasesUnavailable
import com.mcosta.mybooks.data.releases.authorCounts
import com.mcosta.mybooks.data.releases.genreCounts
import com.mcosta.mybooks.data.releases.publisherCounts
import com.mcosta.mybooks.domain.searchKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** The authors, publishers and genres matching the search, with their books in all months. */
data class SearchResults(
    val authors: List<NameCount> = emptyList(),
    val publishers: List<NameCount> = emptyList(),
    val genres: List<NameCount> = emptyList(),
) {
    val isEmpty: Boolean get() = authors.isEmpty() && publishers.isEmpty() && genres.isEmpty()
}

data class ReleasesSearchState(
    val loading: Boolean = false,
    /** Months downloaded so far, of [total]. */
    val loaded: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    /** Months that couldn't be loaded (searched without them). */
    val missing: List<String> = emptyList(),
    /** Set when a saved copy is searched because a download failed. */
    val savedAt: Instant? = null,
    /** The months searched. */
    val months: Int = 0,
    val query: String = "",
    val results: SearchResults = SearchResults(),
)

/** New in Portugal's search: authors, publishers and genres across every month. */
class ReleasesSearchViewModel(private val repo: ReleasesRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReleasesSearchState())
    val state: StateFlow<ReleasesSearchState> = _state.asStateFlow()

    /** Each type's names with their search keys, computed once per load (thousands of names, every keystroke). */
    private var authors = emptyList<Pair<String, NameCount>>()
    private var publishers = emptyList<Pair<String, NameCount>>()
    private var genres = emptyList<Pair<String, NameCount>>()

    init {
        retry()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query, results = results(query)) }

    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val all = repo.allMonths { done, total -> _state.update { it.copy(loaded = done, total = total) } }
                val books = all.months.flatMap { it.books }
                authors = authorCounts(books).keyed()
                publishers = publisherCounts(books).keyed()
                genres = genreCounts(books).keyed()
                _state.update {
                    it.copy(missing = all.missing, savedAt = all.savedAt, months = all.months.size, results = results(it.query))
                }
            } catch (e: ReleasesUnavailable) {
                _state.update { it.copy(error = LOAD_ERROR) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun results(query: String): SearchResults {
        val q = searchKey(query.trim())
        if (q.isEmpty()) return SearchResults()
        fun List<Pair<String, NameCount>>.matching() = filter { it.first.contains(q) }.map { it.second }
        return SearchResults(authors.matching(), publishers.matching(), genres.matching())
    }

    private fun List<NameCount>.keyed() = map { searchKey(it.label) to it }
}
