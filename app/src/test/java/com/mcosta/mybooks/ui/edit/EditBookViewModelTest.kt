package com.mcosta.mybooks.ui.edit

import com.mcosta.mybooks.MainDispatcherRule
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.FakeBookDao
import com.mcosta.mybooks.data.SaveResult
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class EditBookViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val dao = FakeBookDao()
    private var lookupResult: LookupResult = LookupResult.NotFound
    private val repo = BookRepository(dao, object : BookLookupService {
        override suspend fun lookup(isbn: String) = lookupResult
    })

    @Test
    fun lookupFoundPrefillsForm() = runTest {
        lookupResult = LookupResult.Found(
            Book(isbn = "9780441013593", title = "Dune", authors = "Frank Herbert", publishedYear = 1990, pageCount = 535)
        )
        val vm = EditBookViewModel(repo, bookId = null, isbn = "9780441013593")
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals("Dune", s.form.title)
        assertEquals("Frank Herbert", s.form.authors)
        assertEquals("1990", s.form.year)
        assertEquals("535", s.form.pages)
        assertEquals("9780441013593", s.form.isbn)
        assertEquals(ReadingStatus.TO_READ, s.form.status)
        assertNull(s.message)
    }

    @Test
    fun lookupNotFoundKeepsIsbnAndShowsMessage() = runTest {
        val vm = EditBookViewModel(repo, bookId = null, isbn = "9780306406157")
        assertEquals("9780306406157", vm.state.value.form.isbn)
        assertEquals("", vm.state.value.form.title)
        assertEquals("Book not found — enter the details manually", vm.state.value.message)
    }

    @Test
    fun lookupErrorShowsOfflineMessage() = runTest {
        lookupResult = LookupResult.Error(IOException("offline"))
        val vm = EditBookViewModel(repo, bookId = null, isbn = "9780306406157")
        assertEquals("Couldn't reach the book databases — enter the details manually", vm.state.value.message)
    }

    @Test
    fun editLoadsExistingBook() = runTest {
        val id = (repo.save(Book(title = "Emma", rating = 4, notes = "n")) as SaveResult.Saved).id
        val vm = EditBookViewModel(repo, bookId = id, isbn = null)
        assertEquals("Emma", vm.state.value.form.title)
        assertEquals(4, vm.state.value.form.rating)
        vm.update { it.copy(title = "Emma (2nd)") }
        vm.save()
        assertEquals(id, vm.state.value.savedId)
        val saved = repo.observeBook(id).first()!!
        assertEquals("Emma (2nd)", saved.title)
        assertEquals("n", saved.notes)
    }

    @Test
    fun duplicateIsbnShowsError() = runTest {
        repo.save(Book(title = "Dune", isbn = "9780441013593"))
        val vm = EditBookViewModel(repo, bookId = null, isbn = null)
        vm.update { it.copy(title = "Other", isbn = "9780441013593") }
        vm.save()
        assertEquals("This ISBN is already in your library", vm.state.value.error)
        assertNull(vm.state.value.savedId)
    }

    @Test
    fun blankTitleShowsError() = runTest {
        val vm = EditBookViewModel(repo, bookId = null, isbn = null)
        vm.save()
        assertEquals("Title is required", vm.state.value.error)
    }

    @Test
    fun invalidIsbnShowsError() = runTest {
        val vm = EditBookViewModel(repo, bookId = null, isbn = null)
        vm.update { it.copy(title = "X", isbn = "123") }
        vm.save()
        assertEquals("Invalid ISBN", vm.state.value.error)
    }

    @Test
    fun saveSuccessSetsSavedId() = runTest {
        val vm = EditBookViewModel(repo, bookId = null, isbn = null)
        vm.update { it.copy(title = "New", status = ReadingStatus.WISHLIST, rating = 3) }
        vm.save()
        val id = vm.state.value.savedId
        assertNotNull(id)
        val saved = repo.observeBook(id!!).first()!!
        assertEquals(ReadingStatus.WISHLIST, saved.status)
        assertEquals(3, saved.rating)
    }

    @Test
    fun nonNumericYearIgnoredAsNull() = runTest {
        val vm = EditBookViewModel(repo, bookId = null, isbn = null)
        vm.update { it.copy(title = "X", year = "abc", pages = " 120 ") }
        vm.save()
        val saved = repo.observeBook(vm.state.value.savedId!!).first()!!
        assertNull(saved.publishedYear)
        assertEquals(120, saved.pageCount)
    }
}
