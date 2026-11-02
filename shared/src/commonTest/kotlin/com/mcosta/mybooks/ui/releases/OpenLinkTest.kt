package com.mcosta.mybooks.ui.releases

import androidx.compose.ui.platform.UriHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenLinkTest {
    @Test
    fun openFailureIsReported() {
        val noBrowser = object : UriHandler { override fun openUri(uri: String) = throw IllegalStateException("no activity") }
        assertFalse(noBrowser.tryOpen("https://www.wook.pt/pesquisa?keyword=9789895682799"))
    }

    @Test
    fun openSuccess() {
        val opened = mutableListOf<String>()
        val browser = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        assertTrue(browser.tryOpen("https://x"))
        assertEquals(listOf("https://x"), opened)
    }
}
