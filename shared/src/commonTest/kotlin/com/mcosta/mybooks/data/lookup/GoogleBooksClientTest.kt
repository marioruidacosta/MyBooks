package com.mcosta.mybooks.data.lookup

import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class GoogleBooksClientTest {
    private val server = FakeHttpServer()
    private lateinit var client: GoogleBooksClient

    @BeforeTest
    fun setUp() {
        client = GoogleBooksClient(server.httpClient(), server.baseUrl)
    }


    @Test
    fun googleFoundMapsAllFields() = runTest {
        server.enqueue(json("google_found.json"))
        val book = (client.lookup("9780441013593") as LookupResult.Found).book
        assertEquals("Dune: Deluxe Edition", book.title)
        assertEquals("Frank Herbert", book.authors)
        assertEquals("Ace", book.publisher)
        assertEquals(1990, book.publishedYear)
        assertEquals(535, book.pageCount)
        assertEquals("https://books.google.com/books/content?id=B1hSG45JCX4C&zoom=1", book.coverUrl)
        assertEquals("Set on the desert planet Arrakis.", book.description)
        assertEquals("9780441013593", book.isbn)
        assertEquals("/books/v1/volumes?q=isbn:9780441013593", server.takeRequest().path)
    }

    @Test
    fun apiKeyIsSentWhenConfigured() = runTest {
        val keyed = GoogleBooksClient(server.httpClient(), server.baseUrl, apiKey = "abc-123")
        server.enqueue(json("google_found.json"))
        keyed.lookup("9780441013593")
        assertEquals("/books/v1/volumes?q=isbn:9780441013593&key=abc-123", server.takeRequest().path)
    }

    @Test
    fun blankApiKeyIsNotSent() = runTest {
        val unkeyed = GoogleBooksClient(server.httpClient(), server.baseUrl, apiKey = " ")
        server.enqueue(json("google_found.json"))
        unkeyed.lookup("9780441013593")
        assertEquals("/books/v1/volumes?q=isbn:9780441013593", server.takeRequest().path)
    }

    @Test
    fun googlePartialGivesNulls() = runTest {
        server.enqueue(json("google_partial.json"))
        val book = (client.lookup("9780306406157") as LookupResult.Found).book
        assertEquals("Anonymous Pamphlet", book.title)
        assertEquals("", book.authors)
        assertEquals(2005, book.publishedYear)
        assertNull(book.coverUrl)
        assertNull(book.pageCount)
        assertNull(book.publisher)
    }

    @Test
    fun controlCharactersStripped() = runTest {
        server.enqueue(
            FakeResponse().setBody(
                """{"items":[{"volumeInfo":{"title":"\u0098O \u009cMemorial","authors":["José\u0009 Saramago"],"publisher":"Caminho\u0000"}}]}"""
            )
        )
        val book = (client.lookup("9789722100000") as LookupResult.Found).book
        assertEquals("O Memorial", book.title)
        assertEquals("José Saramago", book.authors)
        assertEquals("Caminho", book.publisher)
    }

    @Test
    fun googleZeroItemsIsNotFound() = runTest {
        server.enqueue(json("google_empty.json"))
        assertEquals(LookupResult.NotFound, client.lookup("9780306406157"))
    }

    @Test
    fun googleServerErrorIsError() = runTest {
        server.enqueue(FakeResponse().setResponseCode(500))
        assertTrue(client.lookup("9780306406157") is LookupResult.Error)
    }

    @Test
    fun malformedJsonIsError() = runTest {
        server.enqueue(FakeResponse().setBody("<html>oops</html>"))
        assertTrue(client.lookup("9780306406157") is LookupResult.Error)
    }
}
