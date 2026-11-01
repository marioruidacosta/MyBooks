package com.mcosta.mybooks.ui.social

import com.mcosta.mybooks.socialFor
import com.mcosta.mybooks.social.FakeSocial
import com.mcosta.mybooks.social.SocialConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SocialEntryPointsTest {
    private val engine = MockEngine { respondOk() }

    @Test
    fun socialHiddenWhenNotConfigured() {
        var created = 0
        assertNull(socialFor(SocialConfig("", ""), engine) { _, _ -> created++; FakeSocial().services })
        assertEquals(0, created)
    }

    @Test
    fun socialShownWhenConfigured() {
        var created = 0
        assertNotNull(socialFor(SocialConfig("https://x.supabase.co", "anon"), engine) { _, _ -> created++; FakeSocial().services })
        assertEquals(1, created)
    }
}
