package com.mcosta.mybooks.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookDao()
    }

    @After
    fun tearDown() = db.close()

    private fun book(title: String, authors: String = "", isbn: String? = null, status: ReadingStatus = ReadingStatus.TO_READ) =
        Book(title = title, authors = authors, isbn = isbn, status = status).toEntity()

    @Test
    fun observeFiltersByStatus() = runTest {
        dao.insert(book("A", status = ReadingStatus.READ))
        dao.insert(book("B", status = ReadingStatus.WISHLIST))
        val read = dao.observe(ReadingStatus.READ, "").first()
        assertEquals(listOf("A"), read.map { it.title })
        assertEquals(2, dao.observe(null, "").first().size)
    }

    @Test
    fun searchMatchesTitleOrAuthorIgnoringCase() = runTest {
        dao.insert(book("Dune", authors = "Frank Herbert"))
        dao.insert(book("Emma", authors = "Jane Austen"))
        assertEquals(listOf("Dune"), dao.observe(null, searchPattern("dune")).first().map { it.title })
        assertEquals(listOf("Dune"), dao.observe(null, searchPattern("HERBERT")).first().map { it.title })
    }

    @Test
    fun searchIgnoresCaseOfAccentedLetters() = runTest {
        dao.insert(book("Ética a Nicómaco", authors = "Aristóteles"))
        dao.insert(book("Dune"))
        assertEquals(listOf("Ética a Nicómaco"), dao.observe(null, searchPattern("ética")).first().map { it.title })
        assertEquals(listOf("Ética a Nicómaco"), dao.observe(null, searchPattern("ÉTICA")).first().map { it.title })
    }

    @Test
    fun searchIgnoresAccents() = runTest {
        dao.insert(book("Memorial do Convento", authors = "José Saramago"))
        assertEquals(1, dao.observe(null, searchPattern("jose")).first().size)
        assertEquals(1, dao.observe(null, searchPattern("Jósé")).first().size)
    }

    @Test
    fun searchTreatsPercentLiterally() = runTest {
        dao.insert(book("100% Wolf"))
        dao.insert(book("1000 Wolves"))
        assertEquals(listOf("100% Wolf"), dao.observe(null, searchPattern("100%")).first().map { it.title })
    }

    @Test
    fun searchTreatsUnderscoreLiterally() = runTest {
        dao.insert(book("a_b"))
        dao.insert(book("axb"))
        assertEquals(listOf("a_b"), dao.observe(null, searchPattern("a_b")).first().map { it.title })
    }

    @Test
    fun resultsOrderedByTitleIgnoringCase() = runTest {
        dao.insert(book("banana"))
        dao.insert(book("Apple"))
        assertEquals(listOf("Apple", "banana"), dao.observe(null, "").first().map { it.title })
    }

    @Test
    fun findByIsbn() = runTest {
        dao.insert(book("Dune", isbn = "9780441013593"))
        assertNotNull(dao.findByIsbn("9780441013593"))
        assertNull(dao.findByIsbn("9780306406157"))
    }

    @Test
    fun findWithoutIsbnMatchesTitleAndAuthorsIgnoringCase() = runTest {
        dao.insert(book("Family Recipes", authors = "Grandma"))
        dao.insert(book("Dune", authors = "Frank Herbert", isbn = "9780441013593"))
        assertNotNull(dao.findWithoutIsbn("family recipes", "GRANDMA"))
        assertNull(dao.findWithoutIsbn("Family Recipes", "Someone else"))
        assertNull(dao.findWithoutIsbn("Dune", "Frank Herbert"))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun uniqueIsbnEnforced() = runTest {
        dao.insert(book("Dune", isbn = "9780441013593"))
        dao.insert(book("Dune again", isbn = "9780441013593"))
    }

    @Test
    fun mappingRoundTripKeepsFields() {
        val original = Book(
            id = 7, isbn = "9780441013593", title = "Dune", authors = "Frank Herbert", publisher = "Ace",
            publishedYear = 1990, pageCount = 535, coverUrl = "https://x/y.jpg", description = "d",
            status = ReadingStatus.READ, rating = 5, notes = "great", dateAdded = 1, dateRead = 2,
        )
        assertEquals(original, original.toEntity().toBook())
    }
}
