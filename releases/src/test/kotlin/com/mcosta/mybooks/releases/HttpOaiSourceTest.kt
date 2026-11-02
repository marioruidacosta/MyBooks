package com.mcosta.mybooks.releases

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** HttpOaiSource against a local HTTP server. */
class HttpOaiSourceTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val base get() = "http://127.0.0.1:${server.address.port}"
    private val page = Fixtures.stream("october.xml").readBytes()

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun followsRedirects() {
        server.createContext("/old") { ex ->
            ex.responseHeaders.add("Location", "$base/oai?" + ex.requestURI.rawQuery)
            ex.sendResponseHeaders(301, -1)
            ex.close()
        }
        server.createContext("/oai") { ex ->
            ex.sendResponseHeaders(200, page.size.toLong())
            ex.responseBody.use { it.write(page) }
        }
        server.start()
        val source = HttpOaiSource("$base/old", delayMillis = 0)
        assertEquals(32, source.page(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null).records.size)
    }

    @Test
    fun stalledBodyTimesOut() {
        server.createContext("/oai") { ex ->
            ex.sendResponseHeaders(200, page.size.toLong())
            ex.responseBody.write(page, 0, 100)
            ex.responseBody.flush()
            Thread.sleep(5_000)
            ex.close()
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        val source = HttpOaiSource("$base/oai", delayMillis = 0, timeout = Duration.ofMillis(500))
        assertFailsWith<IOException> { source.page(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null) }
    }
}
