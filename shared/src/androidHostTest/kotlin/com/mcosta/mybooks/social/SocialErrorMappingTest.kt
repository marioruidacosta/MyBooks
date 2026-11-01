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
    fun unknownHostIsOffline() {
        assertEquals(SocialException.Kind.OFFLINE, mapSocialError(UnknownHostException("zwam.supabase.co")).kind)
    }
}
