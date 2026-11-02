package com.mcosta.mybooks.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

/**
 * The app's database file, opened with Android's own SQLite (as before the move to Kotlin
 * Multiplatform), so the existing `mybooks.db` on the phone keeps working unchanged.
 */
fun createAndroidDatabase(context: Context, name: String = "mybooks.db"): AppDatabase =
    createDatabase(
        Room.databaseBuilder<AppDatabase>(context.applicationContext, context.getDatabasePath(name).absolutePath),
        AndroidSQLiteDriver(),
    )
