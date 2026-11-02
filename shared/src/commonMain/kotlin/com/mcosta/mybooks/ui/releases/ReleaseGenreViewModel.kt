package com.mcosta.mybooks.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.releases.ReleaseBook
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
    val books: List<ReleaseBook> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Set when the saved copy is shown because the download failed. */
    val savedAt: Instant? = null,
    /** Whether the month is over, from the saved index (null until known). */
    val monthComplete: Boolean? = null,
)

/** One genre's new books in one month, A–Z. */
class ReleaseGenreViewModel(
    private val repo: ReleasesRepository,
    private val month: String,
    private val genre: ReleaseGenre,
) : ViewModel() {
    private val _state = MutableStateFlow(GenreBooksState())
    val state: StateFlow<GenreBooksState> = _state.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // "(so far)" follows the month list, like the month picker, not the phone's clock.
            val complete = repo.savedMonth(month)?.complete
            _state.update { it.copy(monthComplete = complete) }
            try {
                val cached = repo.books(month)
                _state.update {
                    it.copy(books = cached.value.filter { b -> b.genreType == genre }, savedAt = cached.savedAt.takeIf { cached.offline })
                }
            } catch (e: ReleasesUnavailable) {
                _state.update { it.copy(error = LOAD_ERROR) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
