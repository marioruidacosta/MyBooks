package com.mcosta.mybooks

import android.content.Context
import com.mcosta.mybooks.data.covers.CoverStore
import com.mcosta.mybooks.data.db.createAndroidDatabase
import io.ktor.client.engine.okhttp.OkHttp
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** The Android app's dependencies: existing database file, OkHttp, photos in files/covers. */
fun androidAppContainer(context: Context, googleBooksApiKey: String, appVersion: String) = AppContainer(
    database = createAndroidDatabase(context),
    httpEngine = OkHttp.create(),
    coverStore = CoverStore(FileSystem.SYSTEM, context.filesDir.toOkioPath() / "covers"),
    googleBooksApiKey = googleBooksApiKey,
    userAgent = "MyBooks/$appVersion (Android; personal library app)",
)
