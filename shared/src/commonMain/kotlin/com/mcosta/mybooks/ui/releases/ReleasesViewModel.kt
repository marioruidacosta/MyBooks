package com.mcosta.mybooks.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.releases.NameCount
import com.mcosta.mybooks.data.releases.ReleaseMonth
import com.mcosta.mybooks.data.releases.ReleasesRepository
import com.mcosta.mybooks.data.releases.ReleasesUnavailable
import com.mcosta.mybooks.data.releases.authorCounts
import com.mcosta.mybooks.data.releases.publisherCounts
import com.mcosta.mybooks.data.releases.search
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal const val LOAD_ERROR = "Couldn't load the new releases"

/** How the month's books are grouped on the main screen. */
enum class ReleaseView(val label: String) { GENRE("Genre"), AUTHOR("Author"), PUBLISHER("Publisher") }

data class ReleasesState(
    val months: List<ReleaseMonth> = emptyList(),
    val selected: ReleaseMonth? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Set when the saved copy is shown because the download failed. */
    val savedAt: Instant? = null,
    /** The lists haven't been published yet. */
    val notPublished: Boolean = false,
    val view: ReleaseView = ReleaseView.GENRE,
    /** The selected month's authors or publishers (Author and Publisher views). */
    val names: List<NameCount> = emptyList(),
    val namesLoading: Boolean = false,
    val namesError: String? = null,
    val query: String = "",
) {
    /** [names] matching the search box. */
    val shownNames: List<NameCount> get() = names.search(query)
}

/** New in Portugal's main screen: the month picker and the chosen month's genres, authors or publishers. */
class ReleasesViewModel(private val repo: ReleasesRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReleasesState())
    val state: StateFlow<ReleasesState> = _state.asStateFlow()
    private var namesJob: Job? = null

    init {
        retry()
    }

    fun select(month: String) {
        _state.update { s -> s.copy(selected = s.months.firstOrNull { it.month == month } ?: s.selected) }
        loadNames()
    }

    fun setView(view: ReleaseView) {
        _state.update { it.copy(view = view, query = "", names = emptyList(), namesError = null) }
        loadNames()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun retry() {
        _state.update { it.copy(loading = true, error = null, notPublished = false) }
        viewModelScope.launch {
            try {
                val cached = repo.months()
                _state.update { s ->
                    val selected = cached.value.firstOrNull { it.month == s.selected?.month } ?: cached.value.firstOrNull()
                    s.copy(months = cached.value, selected = selected, savedAt = cached.savedAt.takeIf { cached.offline })
                }
            } catch (e: ReleasesUnavailable) {
                _state.update { if (e.notPublished) it.copy(notPublished = true) else it.copy(error = LOAD_ERROR) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
            loadNames()
        }
    }

    /** Authors or publishers come from the month file (downloaded on demand), not the index. */
    fun loadNames() {
        val s = _state.value
        val month = s.selected?.month
        namesJob?.cancel()
        if (s.view == ReleaseView.GENRE || month == null) {
            _state.update { it.copy(namesLoading = false) }
            return
        }
        _state.update { it.copy(namesLoading = true, namesError = null) }
        namesJob = viewModelScope.launch {
            try {
                val books = repo.books(month).value
                val names = if (s.view == ReleaseView.AUTHOR) authorCounts(books) else publisherCounts(books)
                _state.update { if (it.view == s.view && it.selected?.month == month) it.copy(names = names) else it }
            } catch (e: ReleasesUnavailable) {
                _state.update { it.copy(namesError = LOAD_ERROR) }
            } finally {
                _state.update { it.copy(namesLoading = false) }
            }
        }
    }
}
