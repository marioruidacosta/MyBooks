package com.mcosta.mybooks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF6D4C41), secondary = Color(0xFF8D6E63))
private val Dark = darkColorScheme(primary = Color(0xFFD7B8A8), secondary = Color(0xFFBCAAA4))

/** The platform's own colours (Android 12+ wallpaper colours), or null to use the app's brown theme. */
@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme?

/** Material 3 theme. */
@Composable
fun MyBooksTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = platformColorScheme(dark) ?: if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, content = content)
}
