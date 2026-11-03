package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.goodreads.Csv
import com.mcosta.mybooks.data.goodreads.GoodreadsCsv
import com.mcosta.mybooks.data.goodreads.GoodreadsFormatException
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.data.releases.openLibraryCover
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import com.mcosta.mybooks.testing.fixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BookRepositoryGoodreadsTest {
    private val dao = FakeBookDao()
    private val repo = BookRepository(dao, object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    }, clock = { 1000L })
    private val export = fixture("goodreads_export.csv")

    private suspend fun saved(book: Book) = (repo.save(book) as SaveResult.Saved).id

    @Test
    fun importAddsNewBooksAndSkipsUntitledRows() = runTest {
        val summary = repo.importGoodreads(export)
        assertEquals(ImportSummary(added = 4, updated = 0, failed = 1), summary)
        val dune = repo.findByIsbn("9780441013593")!!
        assertEquals(ReadingStatus.READ, dune.status)
        assertEquals(openLibraryCover("9780441013593"), dune.coverUrl)
        assertNull(repo.findByTitleAndAuthors("Good Omens", "Terry Pratchett, Neil Gaiman")!!.coverUrl)
    }

    @Test
    fun importUpdatesReadingButKeepsMyBooksDetails() = runTest {
        val id = saved(
            Book(
                title = "Dune", isbn = "9780441013593", authors = "Frank Herbert", coverUrl = "file://cover.jpg",
                description = "Desert planet.", notes = "mine", rating = 3, publisher = "Gollancz", status = ReadingStatus.TO_READ,
            ),
        )
        val summary = repo.importGoodreads(export)
        assertEquals(ImportSummary(added = 3, updated = 1, failed = 1), summary)
        val dune = repo.getBook(id)!!
        assertEquals(ReadingStatus.READ, dune.status)
        assertEquals(5, dune.rating)
        assertEquals("Loved it.\n\nSpice & \"sand\".", dune.review)
        assertEquals("file://cover.jpg", dune.coverUrl)
        assertEquals("Desert planet.", dune.description)
        assertEquals("mine", dune.notes)
        assertEquals("Gollancz", dune.publisher)          // only empty details are filled in
        assertEquals(688, dune.pageCount)
        assertEquals("Dune", dune.title)
    }

    @Test
    fun unratedInGoodreadsKeepsMyRating() = runTest {
        val id = saved(Book(title = "Good Omens", authors = "Terry Pratchett, Neil Gaiman", rating = 4, review = "Funny."))
        repo.importGoodreads(export)
        val omens = repo.getBook(id)!!
        assertEquals(4, omens.rating)
        assertEquals("Funny.", omens.review)
        assertEquals(ReadingStatus.READING, omens.status)
    }

    @Test
    fun readWithoutDateReadUsesDateAddedNotToday() = runTest {
        val csv = Csv.write(
            listOf(
                listOf("Title", "Author", "Exclusive Shelf", "Date Read", "Date Added"),
                listOf("Undated", "A", "read", "", "2019/05/04"),
            ),
        )
        repo.importGoodreads(csv)
        val book = repo.findByTitleAndAuthors("Undated", "A")!!
        assertEquals(book.dateAdded, book.dateRead)
        assertNotEquals(1000L, book.dateRead)
    }

    @Test
    fun importingTwiceDoesNotDuplicate() = runTest {
        repo.importGoodreads(export)
        assertEquals(ImportSummary(added = 0, updated = 4, failed = 1), repo.importGoodreads(export))
        assertEquals(4, repo.bookCount())
    }

    @Test
    fun notAGoodreadsFileChangesNothing() = runTest {
        assertFailsWith<GoodreadsFormatException> { repo.importGoodreads("{\"version\":1}") }
        assertEquals(0, repo.bookCount())
    }

    @Test
    fun exportListsTheLibrary() = runTest {
        saved(Book(title = "Emma", authors = "Jane Austen", status = ReadingStatus.WISHLIST))
        saved(Book(title = "Dune", isbn = "9780441013593", rating = 5, status = ReadingStatus.READ))
        val back = GoodreadsCsv.read(repo.exportGoodreads())
        assertEquals(setOf("Emma", "Dune"), back.map { it.title }.toSet())
        assertEquals(ReadingStatus.WISHLIST, back.first { it.title == "Emma" }.status)
        assertEquals(5, back.first { it.title == "Dune" }.rating)
    }
}
