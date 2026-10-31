package com.mcosta.mybooks

import android.content.Context
import androidx.room.withTransaction
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.db.AppDatabase
import com.mcosta.mybooks.data.lookup.BnpClient
import com.mcosta.mybooks.data.lookup.GoogleBooksClient
import com.mcosta.mybooks.data.lookup.LookupChain
import com.mcosta.mybooks.data.lookup.OpenLibraryClient
import com.mcosta.mybooks.data.lookup.lookupHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.mcosta.mybooks.ui.components.coverStoreFor

/** Hand-wired app dependencies, created once by the app's Application class. */
class AppContainer(context: Context, googleBooksApiKey: String, appVersion: String) {
    /** Outlives screens; used for writes that must finish after a screen closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = AppDatabase.create(context)

    private val http = lookupHttpClient("MyBooks/$appVersion (Android; personal library app)")

    val repository = BookRepository(
        dao = db.bookDao(),
        lookupService = LookupChain(
            listOf(
                BnpClient(http),
                GoogleBooksClient(http, apiKey = googleBooksApiKey),
                OpenLibraryClient(http),
            )
        ),
        transactionRunner = { block -> db.withTransaction { block() } },
        coverStore = coverStoreFor(context),
    )
}

/** Implemented by the Application class so ViewModels can reach the [AppContainer]. */
interface ContainerHolder {
    val container: AppContainer
}
