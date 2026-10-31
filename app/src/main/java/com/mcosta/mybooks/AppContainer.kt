package com.mcosta.mybooks

import android.content.Context
import androidx.room.withTransaction
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.db.AppDatabase
import com.mcosta.mybooks.data.lookup.FallbackLookupService
import com.mcosta.mybooks.data.lookup.GoogleBooksClient
import com.mcosta.mybooks.data.lookup.OpenLibraryClient
import com.mcosta.mybooks.data.lookup.lookupHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired app dependencies, created once by [MyBooksApp]. */
class AppContainer(context: Context) {
    /** Outlives screens; used for writes that must finish after a screen closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = AppDatabase.create(context)

    private val http = lookupHttpClient("MyBooks/${BuildConfig.VERSION_NAME} (Android; personal library app)")

    val repository = BookRepository(
        dao = db.bookDao(),
        lookupService = FallbackLookupService(
            GoogleBooksClient(http, apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY),
            OpenLibraryClient(http),
        ),
        transactionRunner = { block -> db.withTransaction { block() } },
    )
}
