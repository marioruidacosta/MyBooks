package com.mcosta.mybooks.data.covers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoverStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val store by lazy { CoverStore(File(tmp.root, "covers")) }

    @Test
    fun saveReturnsCoverRefAndStoresBytes() {
        val ref = store.save(byteArrayOf(1, 2, 3))
        assertTrue(ref.startsWith("cover:") && ref.endsWith(".jpg"))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.fileFor(ref)!!.readBytes())
    }

    @Test
    fun webUrlsAreNotLocal() {
        assertFalse(store.isLocal("https://covers.openlibrary.org/b/id/1-L.jpg"))
        assertNull(store.fileFor("https://covers.openlibrary.org/b/id/1-L.jpg"))
        assertEquals("https://x/y.jpg", store.imageModel("https://x/y.jpg"))
    }

    @Test
    fun imageModelForLocalCoverIsTheFile() {
        val ref = store.save(byteArrayOf(9))
        assertEquals(store.fileFor(ref), store.imageModel(ref))
    }

    @Test
    fun deleteRemovesLocalFileOnly() {
        val ref = store.save(byteArrayOf(9))
        store.delete(ref)
        assertFalse(store.fileFor(ref)!!.exists())
        store.delete("https://x/y.jpg") // no-op, no crash
        store.delete(null)
    }

    @Test
    fun refsCannotEscapeTheDirectory() {
        assertNull(store.fileFor("cover:../library.db"))
        assertNull(store.fileFor("cover:a/b.jpg"))
        assertNull(store.fileFor("cover:"))
    }

    @Test
    fun putUsesGivenName() {
        val ref = store.put("abc.jpg", byteArrayOf(7))
        assertEquals("cover:abc.jpg", ref)
        assertArrayEquals(byteArrayOf(7), store.fileFor(ref)!!.readBytes())
    }
}
