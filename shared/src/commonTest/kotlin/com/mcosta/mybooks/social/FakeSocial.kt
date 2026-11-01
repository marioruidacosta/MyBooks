package com.mcosta.mybooks.social

import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.social.model.Profile
import com.mcosta.mybooks.social.model.Relationship
import com.mcosta.mybooks.social.model.ReportReason
import com.mcosta.mybooks.social.model.SearchResult
import com.mcosta.mybooks.social.model.SessionState
import com.mcosta.mybooks.social.model.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory social network following the same rules as the SQL migration: mutual requests,
 * crossed requests become friends, blocks remove friendships and hide people both ways.
 */
class FakeSocial {
    data class Report(val reporter: String, val reported: String, val reason: ReportReason, val details: String?)

    private val profiles = linkedMapOf<String, Profile>()
    private val emails = mutableMapOf<String, String>()          // email -> user id
    private val pending = mutableSetOf<Pair<String, String>>()   // requester -> addressee
    private val friendsSet = mutableSetOf<Set<String>>()
    private val blocks = mutableSetOf<Pair<String, String>>()    // blocker -> blocked
    val reports = mutableListOf<Report>()
    val uploadedAvatars = mutableListOf<ByteArray>()
    var offline = false
    var validCode = "123456"
    var searchCalls = 0
    var availabilityChecks = 0

    private val sessionFlow = MutableStateFlow<SessionState>(SessionState.SignedOut)

    /** Adds a user with a profile; returns their id. */
    fun addUser(username: String, displayName: String = username.replaceFirstChar { it.uppercase() }): String {
        val id = "id-$username"
        profiles[id] = Profile(id, username, displayName, null, Visibility.FRIENDS)
        return id
    }

    /** Signs in as [userId] directly (with or without a profile). */
    fun signInAs(userId: String) {
        sessionFlow.value = SessionState.SignedIn(userId, null)
    }

    private fun me(): String = (sessionFlow.value as? SessionState.SignedIn)?.userId
        ?: throw SocialException(SocialException.Kind.OTHER, "not signed in")

    private fun check() {
        if (offline) throw SocialException(SocialException.Kind.OFFLINE, "offline")
    }

    private fun blockedBetween(a: String, b: String) = (a to b) in blocks || (b to a) in blocks

    private fun person(p: Profile) = Person(p.id, p.username, p.displayName, p.avatarUrl)

    private fun relationshipOf(me: String, other: String) = when {
        setOf(me, other) in friendsSet -> Relationship.FRIENDS
        (me to other) in pending -> Relationship.REQUEST_SENT
        (other to me) in pending -> Relationship.REQUEST_RECEIVED
        else -> Relationship.NONE
    }

    fun relationshipBetween(a: String, b: String) = relationshipOf(a, b)

    val auth = object : AuthService {
        override val session: StateFlow<SessionState> = sessionFlow
        override suspend fun sendCode(email: String) = check()
        override suspend fun verifyCode(email: String, code: String) {
            check()
            if (code != validCode) throw SocialException(SocialException.Kind.INVALID_CODE, "bad code")
            val id = emails.getOrPut(email) { "id-new-${emails.size}" }
            sessionFlow.value = SessionState.SignedIn(id, email)
        }
        override suspend fun signOut() {
            sessionFlow.value = SessionState.SignedOut
        }
    }

    val profileService = object : ProfileService {
        override suspend fun myProfile(): Profile? { check(); return profiles[me()] }
        override suspend fun isUsernameAvailable(username: String): Boolean {
            check(); availabilityChecks++; return profiles.values.none { it.username == username }
        }
        override suspend fun createProfile(username: String, displayName: String): Profile {
            check()
            if (profiles.values.any { it.username == username }) throw SocialException(SocialException.Kind.USERNAME_TAKEN, "taken")
            return Profile(me(), username, displayName, null, Visibility.FRIENDS).also { profiles[it.id] = it }
        }
        override suspend fun updateProfile(displayName: String, visibility: Visibility): Profile {
            check(); return profiles.getValue(me()).copy(displayName = displayName, visibility = visibility).also { profiles[it.id] = it }
        }
        override suspend fun uploadAvatar(jpeg: ByteArray): Profile {
            check(); uploadedAvatars += jpeg
            return profiles.getValue(me()).copy(avatarUrl = "https://avatars/${me()}.jpg").also { profiles[it.id] = it }
        }
        override suspend fun person(userId: String): Person? {
            check(); return profiles[userId]?.takeUnless { blockedBetween(me(), userId) }?.let(::person)
        }
        override suspend fun deleteAccount() {
            check()
            val id = me()
            profiles.remove(id)
            pending.removeAll { id in it.toList() }
            friendsSet.removeAll { id in it }
            blocks.removeAll { it.first == id }
            sessionFlow.value = SessionState.SignedOut
        }
    }

    val friendsService = object : FriendsService {
        override suspend fun friends(): List<Person> {
            check(); val me = me(); return friendsSet.filter { me in it }.mapNotNull { pair -> profiles[(pair - me).single()]?.let(::person) }
        }
        override suspend fun incomingRequests(): List<Person> {
            check(); val me = me(); return pending.filter { it.second == me }.mapNotNull { profiles[it.first]?.let(::person) }
        }
        override suspend fun outgoingRequests(): List<Person> {
            check(); val me = me(); return pending.filter { it.first == me }.mapNotNull { profiles[it.second]?.let(::person) }
        }
        override suspend fun search(query: String): List<SearchResult> {
            check()
            val q = query.trim()
            if (q.length < 2) return emptyList()
            searchCalls++
            val me = me()
            return profiles.values
                .filter { it.id != me && !blockedBetween(me, it.id) }
                .filter { it.username.contains(q, ignoreCase = true) || it.displayName.contains(q, ignoreCase = true) }
                .map { SearchResult(person(it), relationshipOf(me, it.id)) }
        }
        override suspend fun relationship(userId: String): Relationship { check(); return relationshipOf(me(), userId) }
        override suspend fun sendRequest(userId: String): Relationship {
            check()
            val me = me()
            if (userId == me || userId !in profiles || blockedBetween(me, userId)) {
                throw SocialException(SocialException.Kind.NOT_AVAILABLE, "not available")
            }
            when (relationshipOf(me, userId)) {
                Relationship.REQUEST_RECEIVED -> { pending -= (userId to me); friendsSet += setOf(me, userId) }
                Relationship.NONE -> pending += (me to userId)
                else -> Unit
            }
            return relationshipOf(me, userId)
        }
        override suspend fun respond(requesterId: String, accept: Boolean) {
            check()
            val me = me()
            if (!pending.remove(requesterId to me)) throw SocialException(SocialException.Kind.NOT_AVAILABLE, "no request")
            if (accept) friendsSet += setOf(me, requesterId)
        }
        override suspend fun cancelRequest(userId: String) { check(); pending -= (me() to userId) }
        override suspend fun removeFriend(userId: String) { check(); friendsSet -= setOf(me(), userId) }
    }

    val safetyService = object : SafetyService {
        override suspend fun block(userId: String) {
            check()
            val me = me()
            blocks += (me to userId)
            pending.removeAll { it == (me to userId) || it == (userId to me) }
            friendsSet -= setOf(me, userId)
        }
        override suspend fun unblock(userId: String) { check(); blocks -= (me() to userId) }
        override suspend fun blockedPeople(): List<Person> {
            check(); val me = me(); return blocks.filter { it.first == me }.mapNotNull { profiles[it.second]?.let(::person) }
        }
        override suspend fun report(userId: String, reason: ReportReason, details: String?) {
            check(); reports += Report(me(), userId, reason, details)
        }
    }

    val services = SocialServices(auth, profileService, friendsService, safetyService)
}
