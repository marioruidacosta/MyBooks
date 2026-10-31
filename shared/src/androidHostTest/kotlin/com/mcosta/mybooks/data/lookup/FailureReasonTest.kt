package com.mcosta.mybooks.data.lookup

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FailureReasonTest {
    @Test fun quota() = assertEquals("daily request limit reached", failureReason(HttpStatusException(429)))

    @Test fun badKey400() = assertEquals("API key rejected", failureReason(HttpStatusException(400)))

    @Test fun badKey403() = assertEquals("API key rejected", failureReason(HttpStatusException(403)))

    @Test fun serverError() = assertEquals("server error (HTTP 503)", failureReason(HttpStatusException(503)))

    @Test fun offline() = assertEquals("no internet connection", failureReason(UnknownHostException("openlibrary.org")))

    @Test fun timeout() = assertEquals("request timed out", failureReason(SocketTimeoutException()))

    @Test fun ktorTimeout() =
        assertEquals("request timed out", failureReason(io.ktor.client.plugins.HttpRequestTimeoutException("https://x", 15_000)))

    @Test fun otherIo() = assertEquals("network error", failureReason(IOException("reset")))

    @Test fun badResponse() = assertEquals("unexpected response", failureReason(SerializationException("x")))
}
