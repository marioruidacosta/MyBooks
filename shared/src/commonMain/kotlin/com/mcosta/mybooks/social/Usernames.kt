package com.mcosta.mybooks.social

private val usernamePattern = Regex("^[a-z0-9_.]{3,20}$")

/** What the user typed, as a username: trimmed and lower-case. */
fun normalizeUsername(raw: String): String = raw.trim().lowercase()

/** Why [username] isn't allowed (same rule as the database), or null if it is. */
fun usernameProblem(username: String): String? =
    if (usernamePattern.matches(username)) null else "3–20 characters: a–z, 0–9, _ and ."

/** A short user-facing explanation for a failed social call. */
fun socialMessage(e: Throwable): String = when ((e as? SocialException)?.kind) {
    SocialException.Kind.OFFLINE -> "You're offline"
    SocialException.Kind.INVALID_CODE -> "That code is wrong or has expired"
    SocialException.Kind.USERNAME_TAKEN -> "That username is taken"
    SocialException.Kind.NOT_AVAILABLE -> "This profile isn't available"
    SocialException.Kind.OTHER, null -> "Something went wrong, please try again"
}
