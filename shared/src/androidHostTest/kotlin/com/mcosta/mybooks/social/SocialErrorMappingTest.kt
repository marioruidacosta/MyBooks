package com.mcosta.mybooks.social

import com.mcosta.mybooks.social.supabase.mapSocialError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class SocialErrorMappingTest {
    @Test
    fun tlsFailureIsSecureConnectionNotOffline() {
        val e = mapSocialError(IOException("request failed", SSLHandshakeException("trust anchor for certification path not found")))
        assertEquals(SocialException.Kind.SECURE_CONNECTION, e.kind)
        assertEquals(
            "Couldn't connect securely to the community server. On a work network, try mobile data.",
            socialMessage(e),
        )
    }

    @Test
    fun timeoutIsTimeoutNotOffline() {
        val e = mapSocialError(io.ktor.client.plugins.HttpRequestTimeoutException("https://x.supabase.co/auth/v1/otp", 10_000))
        assertEquals(SocialException.Kind.TIMEOUT, e.kind)
        assertEquals("The community server took too long to answer. Please try again.", socialMessage(e))
    }

    @Test
    fun socketTimeoutIsTimeout() {
        assertEquals(SocialException.Kind.TIMEOUT, mapSocialError(io.ktor.client.network.sockets.SocketTimeoutException("Socket timeout has expired")).kind)
    }

    @Test
    fun wrappedSocketTimeoutIsTimeout() {
        val wrapped = io.github.jan.supabase.exceptions.HttpRequestException(
            "HTTP request to https://x.supabase.co/auth/v1/otp (POST) failed with message: Socket timeout has expired",
            io.ktor.client.request.HttpRequestBuilder(),
        )
        assertEquals(SocialException.Kind.TIMEOUT, mapSocialError(wrapped).kind)
    }

    @Test
    fun clientWaitsThirtySeconds() {
        assertEquals(30_000L, com.mcosta.mybooks.social.supabase.SUPABASE_REQUEST_TIMEOUT.inWholeMilliseconds)
    }

    @Test
    fun unknownHostIsOffline() {
        assertEquals(SocialException.Kind.OFFLINE, mapSocialError(UnknownHostException("zwam.supabase.co")).kind)
    }
}
