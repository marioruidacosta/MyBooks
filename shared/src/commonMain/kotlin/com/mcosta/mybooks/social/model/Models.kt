package com.mcosta.mybooks.social.model

/** Who can see a person's activity (enforced from social part 2). */
enum class Visibility { FRIENDS, PUBLIC }

/** How the signed-in user relates to another person. */
enum class Relationship { NONE, FRIENDS, REQUEST_SENT, REQUEST_RECEIVED }

enum class ReportReason(val label: String) {
    SPAM("Spam"),
    HARASSMENT("Harassment"),
    INAPPROPRIATE("Inappropriate content"),
    IMPERSONATION("Pretending to be someone else"),
    OTHER("Something else"),
}

/** The signed-in user's own profile. */
data class Profile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val visibility: Visibility,
)

/** Another person, as shown in lists, search results and profile pages. */
data class Person(val id: String, val username: String, val displayName: String, val avatarUrl: String?)

data class SearchResult(val person: Person, val relationship: Relationship)

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String, val email: String?) : SessionState
}
