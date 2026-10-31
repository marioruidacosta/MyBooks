package com.mcosta.mybooks.data.lookup

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("/isbn/9780151446476.json", server.takeRequest().path)
        assertEquals("/authors/OL123A.json", server.takeRequest().path)
    }

    /** Open Library serves a 1x1 blank GIF for books without a cover, so no covers → no URL. */
    @Test
    fun editionWithoutCoversHasNoCoverUrl() = runTest {
        server.enqueue(json("openlibrary_edition.json"))
        server.enqueue(json("openlibrary_author.json"))
        assertNull((client.lookup("9780151446476") as LookupResult.Found).book.coverUrl)
    }

    @Test
    fun editionWithCoversUsesFirstCoverId() = runTest {
        server.enqueue(json("openlibrary_edition_with_cover.json"))
        server.enqueue(json("openlibrary_author.json"))
        val book = (client.lookup("9789726084884") as LookupResult.Found).book
        assertEquals("https://covers.example/b/id/14565843-L.jpg", book.coverUrl)
    }

    @Test
    fun controlCharactersStrippedFromTitle() = runTest {
        server.enqueue(json("openlibrary_edition_with_cover.json"))
        server.enqueue(json("openlibrary_author.json"))
        assertEquals("A violência e o escárnio", (client.lookup("9789726084884") as LookupResult.Found).book.title)
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
