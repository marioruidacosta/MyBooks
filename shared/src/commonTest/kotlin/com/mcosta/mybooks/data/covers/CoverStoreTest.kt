package com.mcosta.mybooks.data.covers

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverStoreTest {
    private val fs = FakeFileSystem()
    private val store = CoverStore(fs, "/app/files/covers".toPath())

    @Test
    fun saveReturnsCoverRefAndStoresBytes() {
        val ref = store.save(byteArrayOf(1, 2, 3))
        assertTrue(ref.startsWith("cover:") && ref.endsWith(".jpg"))
        assertContentEquals(byteArrayOf(1, 2, 3), store.read(ref))
    }

    @Test
    fun webUrlsAreNotLocal() {
        assertFalse(store.isLocal("https://covers.openlibrary.org/b/id/1-L.jpg"))
        assertNull(store.fileFor("https://covers.openlibrary.org/b/id/1-L.jpg"))
        assertEquals("https://x/y.jpg", store.imageModel("https://x/y.jpg"))
    }

    @Test
    fun imageModelForLocalCoverIsAFileUrl() {
        val ref = store.put("abc.jpg", byteArrayOf(9))
        assertEquals("file:///app/files/covers/abc.jpg", store.imageModel(ref))
    }

    @Test
    fun deleteRemovesLocalFileOnly() {
        val ref = store.save(byteArrayOf(9))
        store.delete(ref)
        assertNull(store.read(ref))
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
        assertContentEquals(byteArrayOf(7), store.read(ref))
        assertEquals("abc.jpg", store.nameOf(ref))
    }
}
