package com.mcosta.mybooks.ui.settings

import android.net.Uri
import com.mcosta.mybooks.MainDispatcherRule
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.FakeBookDao
import com.mcosta.mybooks.data.backup.BackupSerializer
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val dao = FakeBookDao()
    private val repo = BookRepository(dao, object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    })

    private class FakeIo(var content: String = "", val failRead: Boolean = false) : IoPort {
        val written = mutableMapOf<Uri, String>()
        override suspend fun write(uri: Uri, text: String) { written[uri] = text }
        override suspend fun read(uri: Uri): String = if (failRead) throw IOException("gone") else content
    }

    private val uri = Uri.parse("content://test/backup.json")

    @Test
    fun exportWritesJsonAndReportsCount() = runTest {
        repo.save(Book(title = "Dune"))
        repo.save(Book(title = "Emma"))
        val io = FakeIo()
        val vm = SettingsViewModel(repo, io)
        vm.export(uri)
        assertEquals(listOf("Dune", "Emma"), BackupSerializer.decode(io.written.getValue(uri)).map { it.title })
        assertEquals("Library exported (2 books)", vm.message.value)
    }

    @Test
    fun importReportsSummary() = runTest {
        repo.save(Book(title = "Dune", isbn = "9780441013593"))
        val file = BackupSerializer.encode(
            listOf(Book(title = "Dune", isbn = "9780441013593"), Book(title = "Emma"), Book(title = " ")),
            exportedAt = 0,
        )
        val vm = SettingsViewModel(repo, FakeIo(file))
        vm.import(uri)
        assertEquals("Imported: 1 added, 1 updated, 1 failed", vm.message.value)
    }

    @Test
    fun importBadFileReportsError() = runTest {
        val vm = SettingsViewModel(repo, FakeIo("garbage"))
        vm.import(uri)
        assertEquals("Import failed: Not a MyBooks backup file", vm.message.value)
    }

    @Test
    fun importUnreadableFileReportsError() = runTest {
        val vm = SettingsViewModel(repo, FakeIo(failRead = true))
        vm.import(uri)
        assertEquals("Import failed: gone", vm.message.value)
    }

    @Test
    fun messageCanBeDismissed() = runTest {
        val vm = SettingsViewModel(repo, FakeIo())
        vm.export(uri)
        vm.dismissMessage()
        assertEquals(null, vm.message.value)
    }
}
