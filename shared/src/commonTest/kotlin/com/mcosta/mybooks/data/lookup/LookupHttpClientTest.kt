package com.mcosta.mybooks.data.lookup

import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

class LookupHttpClientTest {
    private val server = FakeHttpServer()


    /** Open Library asks API users to identify their app in the User-Agent. */
    @Test
    fun sendsAppUserAgent() = runTest {
        server.enqueue(json("openlibrary_edition.json"))
        server.enqueue(json("openlibrary_author.json"))
        val client = OpenLibraryClient(lookupHttpClient(server.engine, "MyBooks/1.1 (Android)"), baseUrl = server.baseUrl)
        client.lookup("9780151446476")
        assertEquals("MyBooks/1.1 (Android)", server.takeRequest().getHeader("User-Agent"))
        assertEquals("MyBooks/1.1 (Android)", server.takeRequest().getHeader("User-Agent"))
    }
}
