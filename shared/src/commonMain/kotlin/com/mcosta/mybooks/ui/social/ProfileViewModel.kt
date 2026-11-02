package com.mcosta.mybooks.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.social.SocialServices
import com.mcosta.mybooks.social.model.FeedCursor
import com.mcosta.mybooks.social.model.FeedEntry
import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.social.model.Relationship
import com.mcosta.mybooks.social.model.ReportReason
import com.mcosta.mybooks.social.model.SessionState
import com.mcosta.mybooks.social.model.Visibility
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
    /** Their recent activity (empty while hidden). */
    val activity: List<FeedEntry> = emptyList(),
    val activityNext: FeedCursor? = null,
    /** Friends-only person who isn't a friend: "Only X's friends can see their activity". */
    val activityHidden: Boolean = false,
    /** Non-null while the "who liked this" sheet is open. */
    val likers: List<Person>? = null,
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
        reloadActivity()
    }

    /** Loads the first page of their activity, unless they only share it with friends and aren't one. */
    private suspend fun reloadActivity() {
        val s = _state.value
        val person = s.person ?: return
        val hidden = !s.isMe && person.visibility == Visibility.FRIENDS && s.relationship != Relationship.FRIENDS
        if (hidden) {
            _state.update { it.copy(activity = emptyList(), activityNext = null, activityHidden = true) }
            return
        }
        val page = services.activity.personActivity(userId)
        _state.update { it.copy(activity = page.entries, activityNext = page.next, activityHidden = false) }
    }

    fun loadMoreActivity() {
        val cursor = _state.value.activityNext ?: return
        _state.update { it.copy(activityNext = null) }
        launchAction {
            val page = services.activity.personActivity(userId, cursor)
            _state.update { s -> s.copy(activity = (s.activity + page.entries).distinctBy { it.id }, activityNext = page.next) }
        }
    }

    /** Flips the heart at once; puts it back with a message if the server call fails. */
    fun toggleLike(entryId: Long) {
        val entry = _state.value.activity.firstOrNull { it.id == entryId } ?: return
        if (_state.value.isMe) return  // the server refuses likes on your own entries
        val liking = !entry.likedByMe
        replaceEntry(entryId) { it.copy(likedByMe = liking, likeCount = (it.likeCount + if (liking) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            try {
                if (liking) services.activity.like(entryId) else services.activity.unlike(entryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                replaceEntry(entryId) { it.copy(likedByMe = entry.likedByMe, likeCount = entry.likeCount) }
                _state.update { it.copy(message = socialMessage(e)) }
            }
        }
    }

    fun showLikers(entryId: Long) = launchAction { _state.update { it.copy(likers = services.activity.likers(entryId)) } }

    fun dismissLikers() = _state.update { it.copy(likers = null) }

    private fun replaceEntry(entryId: Long, change: (FeedEntry) -> FeedEntry) =
        _state.update { s -> s.copy(activity = s.activity.map { if (it.id == entryId) change(it) else it }) }

    fun add() = launchAction { _state.update { it.copy(relationship = services.friends.sendRequest(userId)) } }

    fun cancel() = launchAction {
        services.friends.cancelRequest(userId)
        _state.update { it.copy(relationship = Relationship.NONE) }
    }

    fun accept() = launchAction {
        services.friends.respond(userId, accept = true)
        _state.update { it.copy(relationship = Relationship.FRIENDS) }
        reloadActivity()
    }

    fun decline() = launchAction {
        services.friends.respond(userId, accept = false)
        _state.update { it.copy(relationship = Relationship.NONE) }
    }

    fun removeFriend() = launchAction {
        services.friends.removeFriend(userId)
        _state.update { it.copy(relationship = Relationship.NONE) }
        reloadActivity()
    }

    fun block() = launchAction {
        services.safety.block(userId)
        _state.update { it.copy(left = true) }
    }

    /** Details are trimmed, limited to 1000 characters, and sent as null when blank. */
    fun report(reason: ReportReason, details: String, activityId: Long? = null) = launchAction {
        val text = details.trim().take(1000).ifEmpty { null }
        services.safety.report(userId, reason, text, activityId)
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
