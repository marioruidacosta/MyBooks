package com.mcosta.mybooks.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcosta.mybooks.data.covers.PhotoShrinker
import com.mcosta.mybooks.social.SocialServices
import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.social.model.Profile
import com.mcosta.mybooks.social.model.Visibility
import com.mcosta.mybooks.social.socialMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountState(
    val profile: Profile? = null,
    val blocked: List<Person> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    /** True once the community account was deleted; the screen then closes. */
    val deleted: Boolean = false,
    /** True after signing out; the screen then closes. */
    val signedOut: Boolean = false,
)

/** The signed-in user's community account: profile, avatar, visibility, blocked people, sign out, delete. */
class AccountViewModel(
    private val services: SocialServices,
    private val shrinker: PhotoShrinker,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = launchAction {
        val profile = services.profiles.myProfile()
        val blocked = services.safety.blockedPeople()
        _state.update { it.copy(profile = profile, blocked = blocked) }
    }

    fun saveDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(message = "Enter a name") }
            return
        }
        val visibility = _state.value.profile?.visibility ?: Visibility.FRIENDS
        launchAction { _state.update { it.copy(profile = services.profiles.updateProfile(trimmed.take(50), visibility)) } }
    }

    fun setVisibility(visibility: Visibility) {
        val name = _state.value.profile?.displayName ?: return
        launchAction { _state.update { it.copy(profile = services.profiles.updateProfile(name, visibility)) } }
    }

    /** Shrinks the photo to 512 px off the main thread, then uploads it. */
    fun setAvatar(photo: ByteArray) = launchAction {
        val small = withContext(workDispatcher) { shrinker.shrink(photo, maxSide = 512) }
        _state.update { it.copy(profile = services.profiles.uploadAvatar(small)) }
    }

    fun unblock(userId: String) = launchAction {
        services.safety.unblock(userId)
        _state.update { s -> s.copy(blocked = s.blocked.filterNot { it.id == userId }) }
    }

    fun signOut() = launchAction {
        services.auth.signOut()
        _state.update { AccountState(signedOut = true) }
    }

    fun deleteAccount() = launchAction {
        services.profiles.deleteAccount()
        _state.update { AccountState(deleted = true) }
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
