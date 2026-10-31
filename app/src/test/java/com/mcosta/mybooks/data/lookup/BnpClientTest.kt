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

class BnpClientTest {
    private val server = MockWebServer()
    private lateinit var client: BnpClient

    @Before
    fun setUp() {
        server.start()
        client = BnpClient(OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun xml(name: String) = MockResponse().setHeader("Content-Type", "text/xml;charset=utf-8").setBody(fixture(name))

    @Test
    fun foundMapsFields() = runTest {
        server.enqueue(xml("bnp_found.xml"))
        val book = (client.lookup("9789726084884") as LookupResult.Found).book
        assertEquals("A violência e o escárnio", book.title)
        assertEquals("Albert Cossery", book.authors)
        assertEquals("Antígona", book.publisher)
        assertEquals(2026, book.publishedYear)
        assertEquals(181, book.pageCount)
        assertEquals("9789726084884", book.isbn)
        assertEquals(
            "https://rnod.bnportugal.gov.pt/ImagesBN/winlibimg.aspx?skey=&doc=2283108&img=222422&save=true",
            book.coverUrl,
        )
        assertEquals("/isbn/dc/xml?id=9789726084884", server.takeRequest().path)
    }

    @Test
    fun subtitleMarkersRemovedAndCopyrightYearParsed() = runTest {
        server.enqueue(xml("bnp_middlemarch.xml"))
        val book = (client.lookup("9789896412630") as LookupResult.Found).book
        assertEquals("Middlemarch: um estudo da vida de província", book.title)
        assertEquals("George Eliot", book.authors)
        assertEquals("Relógio d'Água", book.publisher)
        assertEquals(2011, book.publishedYear)
        assertEquals(809, book.pageCount)
    }

    @Test
    fun recordWithoutImageHasNoCover() = runTest {
        server.enqueue(xml("bnp_no_cover.xml"))
        assertNull((client.lookup("9789896412630") as LookupResult.Found).book.coverUrl)
    }

    @Test
    fun registoInexistenteIsNotFound() = runTest {
        server.enqueue(xml("bnp_not_found.xml"))
        assertEquals(LookupResult.NotFound, client.lookup("9780000000002"))
    }

    @Test
    fun serverErrorIsError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client.lookup("9789726084884") is LookupResult.Error)
    }

    @Test
    fun malformedXmlIsError() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>maintenance"))
        assertTrue(client.lookup("9789726084884") is LookupResult.Error)
    }

    @Test
    fun authorNameFormatting() {
        assertEquals("Albert Cossery", bnpPersonName("Cossery, Albert, 1913-2008"))
        assertEquals("George Eliot", bnpPersonName("Eliot, George"))
        assertEquals("Miguel Serras Pereira", bnpPersonName("Pereira, Miguel Serras, 1949-"))
        assertEquals("Platão", bnpPersonName("Platão, 427-347 a.C."))
        assertEquals("Camões", bnpPersonName("Camões"))
    }

    @Test
    fun nonSortMarkersRemovedButRealQuestionMarksKept() {
        assertEquals("A violência e o escárnio", bnpTitle("?A ?violência e o escárnio"))
        assertEquals("Porquê?", bnpTitle("Porquê?"))
        assertEquals("Quem? Eu?", bnpTitle("Quem? Eu?"))
    }
}
