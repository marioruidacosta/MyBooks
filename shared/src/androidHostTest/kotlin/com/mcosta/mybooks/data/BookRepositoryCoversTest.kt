package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.backup.BackupFormatException
import com.mcosta.mybooks.data.backup.BackupSerializer
import com.mcosta.mybooks.data.covers.CoverStore
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import java.util.zip.ZipInputStream

class BookRepositoryCoversTest {
    private val fs = FakeFileSystem()

    private val dao = FakeBookDao()
    private val lookup = object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    }
    private val store by lazy { CoverStore(fs, "/covers".toPath()) }
    private val repo by lazy { BookRepository(dao, lookup, clock = { 1000L }, coverStore = store) }

    private suspend fun saveOk(book: Book) = (repo.save(book) as SaveResult.Saved).id

    @Test
    fun setCoverReplacesAndDeletesOldPhoto() = runTest {
        val first = store.save(byteArrayOf(1))
        val id = saveOk(Book(title = "Dune", coverUrl = first))
        val second = store.save(byteArrayOf(2))
        repo.setCover(id, second)
        assertEquals(second, dao.getById(id)!!.coverUrl)
        assertNull(store.read(first))
        assertTrue(store.read(second) != null)
    }

    @Test
    fun savingWithDifferentCoverDeletesOldPhoto() = runTest {
        val photo = store.save(byteArrayOf(1))
        val id = saveOk(Book(title = "Dune", coverUrl = photo))
        saveOk(dao.getById(id)!!.let { Book(id = id, title = "Dune", coverUrl = "https://x/y.jpg") })
        assertNull(store.read(photo))
    }

    @Test
    fun deletingBookDeletesItsPhoto() = runTest {
        val photo = store.save(byteArrayOf(1))
        val id = saveOk(Book(title = "Dune", coverUrl = photo))
        repo.delete(id)
        assertNull(store.read(photo))
    }

    @Test
    fun backupZipContainsLibraryAndPhotos() = runTest {
        val photo = store.save(byteArrayOf(4, 5, 6))
        saveOk(Book(title = "Dune", coverUrl = photo))
        saveOk(Book(title = "Emma", coverUrl = "https://x/emma.jpg"))
        val backup: ByteArray = repo.exportBackup()

        // Read with java.util.zip: the shared writer must produce standard ZIP files.
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(backup.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes() }
        }
        assertEquals(setOf("library.json", "covers/${photo.removePrefix("cover:")}"), entries.keys)
        assertEquals(listOf("Dune", "Emma"), BackupSerializer.decode(entries.getValue("library.json").decodeToString()).map { it.title })
        assertArrayEquals(byteArrayOf(4, 5, 6), entries.getValue("covers/${photo.removePrefix("cover:")}"))
    }

    @Test
    fun restoringZipOnEmptyPhoneBringsBackPhotos() = runTest {
        val photo = store.save(byteArrayOf(4, 5, 6))
        saveOk(Book(title = "Dune", coverUrl = photo))
        val backup = repo.exportBackup()

        val newStore = CoverStore(fs, "/new-phone".toPath())
        val newDao = FakeBookDao()
        val newRepo = BookRepository(newDao, lookup, clock = { 1L }, coverStore = newStore)
        val summary = newRepo.importBackup(backup)

        assertEquals(ImportSummary(added = 1, updated = 0, failed = 0), summary)
        val restored = newDao.getAll().single()
        assertEquals(photo, restored.coverUrl)
        assertArrayEquals(byteArrayOf(4, 5, 6), newStore.read(restored.coverUrl))
    }

    @Test
    fun plainJsonBackupsStillImport() = runTest {
        val json = BackupSerializer.encode(listOf(Book(title = "Old backup")), exportedAt = 0)
        assertEquals(ImportSummary(added = 1, updated = 0, failed = 0), repo.importBackup(json.toByteArray()))
    }

    @Test(expected = BackupFormatException::class)
    fun zipWithoutLibraryIsRejected() = runTest {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("covers/a.jpg")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        repo.importBackup(out.toByteArray())
    }

    @Test
    fun zipEntriesWithUnsafeNamesAreIgnored() = runTest {
        val json = BackupSerializer.encode(listOf(Book(title = "X")), exportedAt = 0)
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("library.json")); zip.write(json.toByteArray()); zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("covers/../../evil.jpg")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        repo.importBackup(out.toByteArray())
        assertFalse(fs.exists("/evil.jpg".toPath()))
    }
}
