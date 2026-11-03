package com.mcosta.mybooks.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs [BookRepository.findMissingCovers] in the app's [scope], so leaving the screen doesn't stop it
 * (a few hundred books take minutes).
 */
class CoverFinder(private val repo: BookRepository, private val scope: CoroutineScope) {
    data class Progress(val done: Int, val total: Int, val found: Int) {
        val finished: Boolean get() = done >= total
    }

    private val _progress = MutableStateFlow<Progress?>(null)
    /** The current or last search; null before the first. */
    val progress: StateFlow<Progress?> = _progress.asStateFlow()
    private var job: Job? = null

    /** Starts a search unless one is running. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                repo.findMissingCovers { done, total, found -> _progress.value = Progress(done, total, found) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A database error ends this search early; it can be started again.
                _progress.value = _progress.value?.let { it.copy(done = it.total) }
            }
        }
    }
}
