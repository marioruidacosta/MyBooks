package com.mcosta.mybooks.ui.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.releases.ReleaseMonth
import com.mcosta.mybooks.data.releases.ReleasesRepository
import com.mcosta.mybooks.data.releases.ReleasesUnavailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal const val LOAD_ERROR = "Couldn't load the new releases"

data class ReleasesState(
    val months: List<ReleaseMonth> = emptyList(),
    val selected: ReleaseMonth? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Set when the saved copy is shown because the download failed. */
    val savedAt: Instant? = null,
    /** The lists haven't been published yet. */
    val notPublished: Boolean = false,
)

/** New in Portugal's main screen: the month picker and the chosen month's genres. */
class ReleasesViewModel(private val repo: ReleasesRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReleasesState())
    val state: StateFlow<ReleasesState> = _state.asStateFlow()

    init {
        retry()
    }

    fun select(month: String) = _state.update { s -> s.copy(selected = s.months.firstOrNull { it.month == month } ?: s.selected) }

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
        }
    }
}
