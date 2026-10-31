package com.mcosta.mybooks.data.lookup

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LookupHttpClientTest {
    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.shutdown()

    /** Open Library asks API users to identify their app in the User-Agent. */
    @Test
    fun sendsAppUserAgent() = runTest {
        server.enqueue(json("openlibrary_edition.json"))
        server.enqueue(json("openlibrary_author.json"))
        val client = OpenLibraryClient(lookupHttpClient("MyBooks/1.1 (Android)"), baseUrl = server.url("/").toString().trimEnd('/'))
        client.lookup("9780151446476")
        assertEquals("MyBooks/1.1 (Android)", server.takeRequest().getHeader("User-Agent"))
        assertEquals("MyBooks/1.1 (Android)", server.takeRequest().getHeader("User-Agent"))
    }
}
