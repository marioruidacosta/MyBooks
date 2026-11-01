package com.mcosta.mybooks.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.social.SocialServices
import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.social.model.Relationship
import com.mcosta.mybooks.social.model.ReportReason
import com.mcosta.mybooks.social.model.SessionState
import com.mcosta.mybooks.social.socialMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val person: Person? = null,
    val relationship: Relationship = Relationship.NONE,
    val isMe: Boolean = false,
    /** The person doesn't exist any more, or a block hides them. */
    val notAvailable: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    /** True after blocking; the screen then closes. */
    val left: Boolean = false,
)

/** Another reader's profile, with the friend action that fits the relationship, block and report. */
class ProfileViewModel(private val services: SocialServices, private val userId: String) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = launchAction {
        val me = (services.auth.session.value as? SessionState.SignedIn)?.userId
        val person = services.profiles.person(userId)
        if (person == null) {
            _state.update { it.copy(person = null, notAvailable = true) }
            return@launchAction
        }
        val relationship = if (me == userId) Relationship.NONE else services.friends.relationship(userId)
        _state.update { it.copy(person = person, relationship = relationship, isMe = me == userId, notAvailable = false) }
    }

    fun add() = launchAction { _state.update { it.copy(relationship = services.friends.sendRequest(userId)) } }

    fun cancel() = launchAction {
        services.friends.cancelRequest(userId)
        _state.update { it.copy(relationship = Relationship.NONE) }
    }

    fun accept() = launchAction {
        services.friends.respond(userId, accept = true)
        _state.update { it.copy(relationship = Relationship.FRIENDS) }
    }

    fun decline() = launchAction {
        services.friends.respond(userId, accept = false)
        _state.update { it.copy(relationship = Relationship.NONE) }
    }

    fun removeFriend() = launchAction {
        services.friends.removeFriend(userId)
        _state.update { it.copy(relationship = Relationship.NONE) }
    }

    fun block() = launchAction {
        services.safety.block(userId)
        _state.update { it.copy(left = true) }
    }

    /** Details are trimmed, limited to 1000 characters, and sent as null when blank. */
    fun report(reason: ReportReason, details: String) = launchAction {
        val text = details.trim().take(1000).ifEmpty { null }
        services.safety.report(userId, reason, text)
        _state.update { it.copy(message = "Thanks, we'll look into it") }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = socialMessage(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
