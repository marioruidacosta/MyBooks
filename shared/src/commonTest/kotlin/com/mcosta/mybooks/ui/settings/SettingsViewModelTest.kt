package com.mcosta.mybooks.ui.settings

import com.mcosta.mybooks.MainDispatcherTest
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.FakeBookDao
import com.mcosta.mybooks.data.backup.BackupSerializer
import com.mcosta.mybooks.data.backup.Zip
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.platform.DocumentPicker
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest : MainDispatcherTest() {
    private val repo = BookRepository(FakeBookDao(), object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    })

    private class FakeDocuments(
        var toOpen: ByteArray? = null,
        val failOpen: Boolean = false,
        val acceptSave: Boolean = true,
    ) : DocumentPicker {
        var savedName: String? = null
        var savedMime: String? = null
        var saved: ByteArray? = null
        override suspend fun save(suggestedName: String, mime: String, bytes: ByteArray): Boolean {
            if (!acceptSave) return false
            savedName = suggestedName; savedMime = mime; saved = bytes
            return true
        }
        override suspend fun open(mimeTypes: List<String>): ByteArray? = if (failOpen) throw IOException("gone") else toOpen
    }

    @Test
    fun exportSavesZipAndReportsCount() = runTest {
        repo.save(Book(title = "Dune"))
        repo.save(Book(title = "Emma"))
        val docs = FakeDocuments()
        val vm = SettingsViewModel(repo, docs, today = { "2026-10-31" })
        vm.export()
        assertEquals("mybooks-backup-2026-10-31.zip", docs.savedName)
        assertEquals("application/zip", docs.savedMime)
        val library = Zip.read(docs.saved!!).toMap().getValue("library.json").decodeToString()
        assertEquals(listOf("Dune", "Emma"), BackupSerializer.decode(library).map { it.title })
        assertEquals("Library exported (2 books)", vm.message.value)
    }

    @Test
    fun cancelledExportShowsNothing() = runTest {
        val vm = SettingsViewModel(repo, FakeDocuments(acceptSave = false), today = { "2026-10-31" })
        vm.export()
        assertNull(vm.message.value)
    }

    @Test
    fun importReportsSummary() = runTest {
        repo.save(Book(title = "Dune", isbn = "9780441013593"))
        val file = BackupSerializer.encode(
            listOf(Book(title = "Dune", isbn = "9780441013593"), Book(title = "Emma"), Book(title = " ")),
            exportedAt = 0,
        )
        val vm = SettingsViewModel(repo, FakeDocuments(toOpen = file.encodeToByteArray()))
        vm.import()
        assertEquals("Imported: 1 added, 1 updated, 1 failed", vm.message.value)
    }

    @Test
    fun cancelledImportShowsNothing() = runTest {
        val vm = SettingsViewModel(repo, FakeDocuments(toOpen = null))
        vm.import()
        assertNull(vm.message.value)
    }

    @Test
    fun importBadFileReportsError() = runTest {
        val vm = SettingsViewModel(repo, FakeDocuments(toOpen = "garbage".encodeToByteArray()))
        vm.import()
        assertEquals("Import failed: Not a MyBooks backup file", vm.message.value)
    }

    @Test
    fun importUnreadableFileReportsError() = runTest {
        val vm = SettingsViewModel(repo, FakeDocuments(failOpen = true))
        vm.import()
        assertEquals("Import failed: gone", vm.message.value)
    }

    @Test
    fun messageCanBeDismissed() = runTest {
        val vm = SettingsViewModel(repo, FakeDocuments(), today = { "2026-10-31" })
        vm.export()
        assertTrue(vm.message.value != null)
        vm.dismissMessage()
        assertNull(vm.message.value)
    }

    @Test
    fun goodreadsExportSavesCsv() = runTest {
        repo.save(Book(title = "Dune"))
        val docs = FakeDocuments()
        val vm = SettingsViewModel(repo, docs, today = { "2026-11-03" })
        vm.exportGoodreads()
        assertEquals("goodreads-2026-11-03.csv", docs.savedName)
        assertEquals("text/csv", docs.savedMime)
        assertTrue(docs.saved!!.decodeToString().startsWith("Title,Author,"))
        assertEquals("Exported 1 book for Goodreads. Upload it on goodreads.com: My Books, Import and export.", vm.message.value)
    }

    @Test
    fun goodreadsImportReportsSummary() = runTest {
        val csv = "Title,Author,Exclusive Shelf\r\nDune,Frank Herbert,read\r\n,Nobody,to-read\r\n"
        val vm = SettingsViewModel(repo, FakeDocuments(toOpen = csv.encodeToByteArray()))
        vm.importGoodreads()
        assertEquals("Imported from Goodreads: 1 added, 0 updated, 1 skipped", vm.message.value)
    }

    @Test
    fun goodreadsImportOfAnotherFileExplains() = runTest {
        val vm = SettingsViewModel(repo, FakeDocuments(toOpen = "{}".encodeToByteArray()))
        vm.importGoodreads()
        assertEquals("Import failed: Not a Goodreads library export", vm.message.value)
    }
}
