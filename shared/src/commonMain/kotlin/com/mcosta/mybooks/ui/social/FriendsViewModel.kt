package com.mcosta.mybooks.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.social.SocialServices
import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.social.model.SearchResult
import com.mcosta.mybooks.social.model.SessionState
import com.mcosta.mybooks.social.socialMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsState(
    val signedIn: Boolean = false,
    val friends: List<Person> = emptyList(),
    val incoming: List<Person> = emptyList(),
    val outgoing: List<Person> = emptyList(),
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val loading: Boolean = false,
    /** Last failure; the lists keep their last loaded content. */
    val message: String? = null,
)

/** Friends, requests and people search. Lists reload whenever the user signs in. */
class FriendsViewModel(private val services: SocialServices) : ViewModel() {
    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            services.auth.session.collect { session ->
                when (session) {
                    is SessionState.SignedIn -> {
                        _state.update { it.copy(signedIn = true) }
                        refresh()
                    }
                    SessionState.SignedOut -> _state.value = FriendsState()
                    SessionState.Loading -> Unit
                }
            }
        }
    }

    fun refresh() = launchAction { reloadLists() }

    private suspend fun reloadLists() {
        val friends = services.friends.friends()
        val incoming = services.friends.incomingRequests()
        val outgoing = services.friends.outgoingRequests()
        _state.update { it.copy(friends = friends, incoming = incoming, outgoing = outgoing) }
    }

    /** Searches 300 ms after typing stops, once the query has at least 2 characters. */
    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch()
        }
    }

    private suspend fun runSearch() {
        val query = _state.value.query
        if (query.trim().length < 2) return
        try {
            val results = services.friends.search(query)
            _state.update { if (it.query == query) it.copy(results = results, message = null) else it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(message = socialMessage(e)) }
        }
    }

    fun accept(userId: String) = launchAction { services.friends.respond(userId, accept = true); afterChange() }

    fun decline(userId: String) = launchAction { services.friends.respond(userId, accept = false); afterChange() }

    fun cancel(userId: String) = launchAction { services.friends.cancelRequest(userId); afterChange() }

    fun add(userId: String) = launchAction { services.friends.sendRequest(userId); afterChange() }

    private suspend fun afterChange() {
        reloadLists()
        runSearch()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = socialMessage(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
