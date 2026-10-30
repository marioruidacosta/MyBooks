package com.mcosta.mybooks.data.lookup

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenLibraryClientTest {
    private val server = MockWebServer()
    private lateinit var client: OpenLibraryClient

    @Before
    fun setUp() {
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        client = OpenLibraryClient(OkHttpClient(), baseUrl = base, coversBaseUrl = "https://covers.example")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun openLibraryResolvesAuthorNames() = runTest {
        server.enqueue(json("openlibrary_edition.json"))
        server.enqueue(json("openlibrary_author.json"))
        val book = (client.lookup("9780151446476") as LookupResult.Found).book
        assertEquals("The Name of the Rose", book.title)
        assertEquals("Umberto Eco", book.authors)
        assertEquals("Harcourt Brace Jovanovich", book.publisher)
        assertEquals(1983, book.publishedYear)
        assertEquals(502, book.pageCount)
        assertEquals("https://covers.example/b/isbn/9780151446476-L.jpg", book.coverUrl)
        assertEquals("/isbn/9780151446476.json", server.takeRequest().path)
        assertEquals("/authors/OL123A.json", server.takeRequest().path)
    }

    @Test
    fun authorFailureStillReturnsBook() = runTest {
        server.enqueue(json("openlibrary_edition.json"))
        server.enqueue(MockResponse().setResponseCode(500))
        val book = (client.lookup("9780151446476") as LookupResult.Found).book
        assertEquals("", book.authors)
    }

    @Test
    fun openLibrary404IsNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(LookupResult.NotFound, client.lookup("9780151446476"))
    }

    @Test
    fun openLibrary503IsError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client.lookup("9780151446476") is LookupResult.Error)
    }
}
