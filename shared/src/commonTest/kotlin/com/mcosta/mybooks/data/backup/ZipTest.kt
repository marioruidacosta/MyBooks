package com.mcosta.mybooks.data.backup

import com.mcosta.mybooks.testing.fixtureBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipTest {
    @Test
    fun crc32OfStandardCheckString() = assertEquals(0xCBF43926u, crc32("123456789".encodeToByteArray()))

    @Test
    fun compressedRoundTrip() {
        val entries = listOf("library.json" to "{\"version\":1}".encodeToByteArray(), "covers/a.jpg" to ByteArray(5000) { (it % 7).toByte() }, "empty" to ByteArray(0))
        val read = Zip.read(Zip.write(entries))
        assertEquals(entries.map { it.first }, read.map { it.first })
        entries.zip(read).forEach { (expected, actual) -> assertContentEquals(expected.second, actual.second) }
    }

    @Test
    fun storedRoundTrip() {
        val entries = listOf("a.txt" to "hello".encodeToByteArray())
        assertContentEquals(entries.single().second, Zip.read(Zip.write(entries, compress = false)).single().second)
    }

    @Test
    fun nonAsciiNamesSurvive() {
        val read = Zip.read(Zip.write(listOf("capas/ação.jpg" to byteArrayOf(1))))
        assertEquals("capas/ação.jpg", read.single().first)
    }

    /** A backup written by the Android app's java.util.zip exporter (DEFLATE entries, data descriptors). */
    @Test
    fun readsBackupMadeByAndroidApp() {
        val entries = Zip.read(fixtureBytes("android_backup.zip")).toMap()
        assertEquals(setOf("library.json", "covers/0f1e2d3c-photo.jpg"), entries.keys)
        assertContentEquals(ByteArray(3000) { (it % 251).toByte() }, entries.getValue("covers/0f1e2d3c-photo.jpg"))
        val books = BackupSerializer.decode(entries.getValue("library.json").decodeToString())
        assertEquals(listOf("A violência e o escárnio", "Dune"), books.map { it.title }.sorted())
    }

    @Test
    fun corruptedDataIsRejected() {
        val zip = Zip.write(listOf("a.txt" to "hello hello hello".encodeToByteArray()), compress = false)
        zip[40] = (zip[40] + 1).toByte() // inside the stored data of the first entry
        assertFailsWith<BackupFormatException> { Zip.read(zip) }
    }

    @Test
    fun notAZipIsRejected() {
        assertFailsWith<BackupFormatException> { Zip.read("{\"version\":1}".encodeToByteArray()) }
    }

    @Test
    fun isZipChecksSignature() {
        assertEquals(true, Zip.isZip(Zip.write(listOf("a" to byteArrayOf(1)))))
        assertEquals(false, Zip.isZip("{}".encodeToByteArray()))
    }
}
