package com.mcosta.mybooks.data

import com.russhwolf.settings.Settings

/** Small values kept between launches, such as the library's sort order. */
interface Preferences {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

/** [Preferences] in the platform's settings store (SharedPreferences, NSUserDefaults). */
class SettingsPreferences(private val settings: Settings) : Preferences {
    override fun get(key: String): String? = settings.getStringOrNull(key)
    override fun set(key: String, value: String) = settings.putString(key, value)
}
