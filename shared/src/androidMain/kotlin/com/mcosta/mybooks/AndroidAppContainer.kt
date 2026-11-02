package com.mcosta.mybooks

import android.content.Context
import com.mcosta.mybooks.data.covers.CoverStore
import com.mcosta.mybooks.data.db.createAndroidDatabase
import com.mcosta.mybooks.social.SocialConfig
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.okhttp.OkHttp
import okio.FileSystem
import java.util.concurrent.TimeUnit
import okio.Path.Companion.toOkioPath

/** The Android app's dependencies: existing database file, OkHttp, photos in files/covers. */
fun androidAppContainer(context: Context, googleBooksApiKey: String, appVersion: String, socialConfig: SocialConfig) = AppContainer(
    database = createAndroidDatabase(context),
    // OkHttp waits 10 s for data by default; sending the sign-in email can take longer.
    httpEngine = OkHttp.create {
        config {
            readTimeout(30, TimeUnit.SECONDS)
            writeTimeout(30, TimeUnit.SECONDS)
        }
    },
    coverStore = CoverStore(FileSystem.SYSTEM, context.filesDir.toOkioPath() / "covers"),
    googleBooksApiKey = googleBooksApiKey,
    userAgent = "MyBooks/$appVersion (Android; personal library app)",
    socialConfig = socialConfig,
    // Session in the app's own preferences (the library default needs an androidx-startup hook).
    socialSettings = SharedPreferencesSettings(context.getSharedPreferences("mybooks_community", Context.MODE_PRIVATE)),
)
