package com.mcosta.mybooks

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.covers.CoverStore
import com.mcosta.mybooks.data.db.AppDatabase
import com.mcosta.mybooks.data.lookup.BnpClient
import com.mcosta.mybooks.data.lookup.GoogleBooksClient
import com.mcosta.mybooks.data.lookup.LookupChain
import com.mcosta.mybooks.data.lookup.OpenLibraryClient
import com.mcosta.mybooks.data.lookup.lookupHttpClient
import com.mcosta.mybooks.social.SocialConfig
import com.mcosta.mybooks.social.SocialServices
import com.mcosta.mybooks.social.supabase.createSocialServices
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired app dependencies, created once per app from the platform's pieces. */
class AppContainer(
    database: AppDatabase,
    httpEngine: HttpClientEngine,
    val coverStore: CoverStore,
    googleBooksApiKey: String,
    userAgent: String,
    socialConfig: SocialConfig = SocialConfig("", ""),
) {
    /** Outlives screens; used for writes that must finish after a screen closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = lookupHttpClient(httpEngine, userAgent)

    val repository = BookRepository(
        dao = database.bookDao(),
        lookupService = LookupChain(
            listOf(
                BnpClient(http),
                GoogleBooksClient(http, apiKey = googleBooksApiKey),
                OpenLibraryClient(http),
            )
        ),
        transactionRunner = { block -> database.useWriterConnection { it.immediateTransaction { block() } } },
        coverStore = coverStore,
    )

    /** The community features, or null when Supabase isn't configured (then they're hidden). */
    val social: SocialServices? = if (socialConfig.enabled) createSocialServices(socialConfig, httpEngine) else null
}
