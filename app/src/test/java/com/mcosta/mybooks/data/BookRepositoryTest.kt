package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRepositoryTest {
    private val dao = FakeBookDao()
    private var now = 1000L
    private val lookup = object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    }
    private val repo = BookRepository(dao, lookup, clock = { now })

    private suspend fun saveOk(book: Book): Long = (repo.save(book) as SaveResult.Saved).id
    private suspend fun get(id: Long) = repo.observeBook(id).first()!!

    @Test
    fun saveSetsDateAdded() = runTest {
        val id = saveOk(Book(title = "Dune"))
        assertEquals(1000L, get(id).dateAdded)
    }

    @Test
    fun updateKeepsDateAdded() = runTest {
        val id = saveOk(Book(title = "Dune"))
        now = 5000L
        saveOk(get(id).copy(title = "Dune Messiah"))
        assertEquals(1000L, get(id).dateAdded)
        assertEquals("Dune Messiah", get(id).title)
    }

    @Test
    fun titleIsTrimmed() = runTest {
        val id = saveOk(Book(title = "  Dune  "))
        assertEquals("Dune", get(id).title)
    }

    @Test
    fun blankTitleRejected() = runTest {
        assertEquals(SaveResult.InvalidTitle, repo.save(Book(title = "   ")))
    }

    @Test
    fun ratingSixRejected() = runTest {
        assertEquals(SaveResult.InvalidRating, repo.save(Book(title = "Dune", rating = 6)))
        assertEquals(SaveResult.InvalidRating, repo.save(Book(title = "Dune", rating = 0)))
    }

    @Test
    fun invalidIsbnRejected() = runTest {
        assertEquals(SaveResult.InvalidIsbn, repo.save(Book(title = "Dune", isbn = "12345")))
    }

    @Test
    fun blankIsbnStoredAsNull() = runTest {
        val id = saveOk(Book(title = "Dune", isbn = "  "))
        assertNull(get(id).isbn)
    }

    @Test
    fun duplicateIsbnRejected() = runTest {
        saveOk(Book(title = "Dune", isbn = "9780441013593"))
        assertEquals(SaveResult.DuplicateIsbn, repo.save(Book(title = "Other", isbn = "9780441013593")))
    }

    @Test
    fun sameBookResaveKeepsIsbnOk() = runTest {
        val id = saveOk(Book(title = "Dune", isbn = "9780441013593"))
        assertTrue(repo.save(get(id).copy(notes = "x")) is SaveResult.Saved)
    }

    @Test
    fun hyphenatedIsbnNormalizedOnSave() = runTest {
        val id = saveOk(Book(title = "X", isbn = "978-0-306-40615-7"))
        assertEquals("9780306406157", get(id).isbn)
        assertEquals(id, repo.findByIsbn("0306406152")?.id)
    }

    @Test
    fun markReadSetsDateRead() = runTest {
        val id = saveOk(Book(title = "Dune"))
        now = 2000L
        repo.setStatus(id, ReadingStatus.READ)
        assertEquals(2000L, get(id).dateRead)
    }

    @Test
    fun savingAsReadSetsDateRead() = runTest {
        val id = saveOk(Book(title = "Dune", status = ReadingStatus.READ))
        assertEquals(1000L, get(id).dateRead)
    }

    @Test
    fun markReadKeepsExistingDateRead() = runTest {
        val id = saveOk(Book(title = "Dune", status = ReadingStatus.READ, dateRead = 42L))
        now = 2000L
        repo.setStatus(id, ReadingStatus.READ)
        assertEquals(42L, get(id).dateRead)
    }

    @Test
    fun leavingReadClearsDateRead() = runTest {
        val id = saveOk(Book(title = "Dune", status = ReadingStatus.READ))
        repo.setStatus(id, ReadingStatus.WISHLIST)
        assertNull(get(id).dateRead)
    }

    @Test
    fun setRatingAndClear() = runTest {
        val id = saveOk(Book(title = "Dune"))
        repo.setRating(id, 4)
        assertEquals(4, get(id).rating)
        repo.setRating(id, null)
        assertNull(get(id).rating)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setRatingOutOfRangeThrows() = runTest {
        val id = saveOk(Book(title = "Dune"))
        repo.setRating(id, 7)
    }

    @Test
    fun setNotes() = runTest {
        val id = saveOk(Book(title = "Dune"))
        repo.setNotes(id, "loved it")
        assertEquals("loved it", get(id).notes)
    }

    @Test
    fun deleteRemoves() = runTest {
        val id = saveOk(Book(title = "Dune"))
        repo.delete(id)
        assertNull(repo.observeBook(id).first())
    }

    @Test
    fun observeBooksEscapesQuery() = runTest {
        saveOk(Book(title = "100% Wolf"))
        saveOk(Book(title = "Dune", status = ReadingStatus.READ))
        assertEquals(listOf("100% Wolf"), repo.observeBooks(null, "100%").first().map { it.title })
        assertEquals(listOf("Dune"), repo.observeBooks(ReadingStatus.READ, "").first().map { it.title })
    }
}
