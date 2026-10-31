package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.backup.BackupFormatException
import com.mcosta.mybooks.data.backup.BackupSerializer
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.test.Test

class BookRepositoryBackupTest {
    private val dao = FakeBookDao()
    private val lookup = object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    }
    private val repo = BookRepository(dao, lookup, clock = { 1000L })

    private fun backup(vararg books: Book) = BackupSerializer.encode(books.toList(), exportedAt = 0)

    @Test
    fun exportContainsAllBooks() = runTest {
        repo.save(Book(title = "Dune"))
        repo.save(Book(title = "Emma"))
        assertEquals(listOf("Dune", "Emma"), BackupSerializer.decode(repo.exportJson()).map { it.title })
    }

    @Test
    fun importAddsAndUpdatesByIsbn() = runTest {
        val id = (repo.save(Book(title = "Dune", isbn = "9780441013593")) as SaveResult.Saved).id
        val summary = repo.importJson(
            backup(
                Book(title = "Dune (updated)", isbn = "9780441013593", rating = 5, dateAdded = 1),
                Book(title = "Emma", status = ReadingStatus.WISHLIST),
            )
        )
        assertEquals(ImportSummary(added = 1, updated = 1, failed = 0), summary)
        val updated = dao.getById(id)!!
        assertEquals("Dune (updated)", updated.title)
        assertEquals(5, updated.rating)
        assertEquals(1000L, updated.dateAdded)
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun importCountsBlankTitleAndInFileDuplicateAsFailed() = runTest {
        val summary = repo.importJson(
            backup(
                Book(title = "A", isbn = "9780306406157"),
                Book(title = "A again", isbn = "9780306406157"),
                Book(title = "  "),
                Book(title = "B", rating = 9),
                Book(title = "C"),
            )
        )
        assertEquals(ImportSummary(added = 2, updated = 0, failed = 3), summary)
        assertEquals(listOf("A", "C"), dao.getAll().map { it.title })
    }

    @Test
    fun importingSameBackupTwiceDoesNotDuplicateBooksWithoutIsbn() = runTest {
        val file = backup(Book(title = "Family Recipes", authors = "Grandma", notes = "v1"), Book(title = "Dune", isbn = "9780441013593"))
        repo.importJson(file)
        val second = repo.importJson(file)
        assertEquals(ImportSummary(added = 0, updated = 2, failed = 0), second)
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun noIsbnMatchIgnoresCaseAndSpaces() = runTest {
        repo.save(Book(title = "Family Recipes", authors = "Grandma"))
        val s = repo.importJson(backup(Book(title = "  family recipes ", authors = "GRANDMA", rating = 5)))
        assertEquals(ImportSummary(added = 0, updated = 1, failed = 0), s)
        assertEquals(5, dao.getAll().single().rating)
    }

    @Test
    fun noIsbnBookDoesNotMatchBookThatHasIsbn() = runTest {
        repo.save(Book(title = "Dune", authors = "Frank Herbert", isbn = "9780441013593"))
        val s = repo.importJson(backup(Book(title = "Dune", authors = "Frank Herbert")))
        assertEquals(ImportSummary(added = 1, updated = 0, failed = 0), s)
    }

    @Test
    fun importInvalidFileWritesNothing() = runTest {
        repo.save(Book(title = "Keep me"))
        try {
            repo.importJson("""{"version":7,"books":[{"title":"X"}]}""")
            fail("expected BackupFormatException")
        } catch (e: BackupFormatException) {
            // expected
        }
        assertEquals(listOf("Keep me"), dao.getAll().map { it.title })
    }

    @Test
    fun importRunsInsideTransaction() = runTest {
        var ran = false
        val txRepo = BookRepository(dao, lookup, clock = { 1L }, transactionRunner = { block -> ran = true; block() })
        txRepo.importJson(backup(Book(title = "X")))
        assertTrue(ran)
    }
}
