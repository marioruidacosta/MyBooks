package com.mcosta.mybooks.social

/** Supabase project URL and public anon key; when either is blank the social features are hidden. */
data class SocialConfig(val url: String, val anonKey: String) {
    val enabled: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}
