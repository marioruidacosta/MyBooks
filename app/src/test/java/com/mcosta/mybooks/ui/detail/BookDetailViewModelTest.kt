@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mcosta.mybooks.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.mcosta.mybooks.MainDispatcherRule
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.FakeBookDao
import com.mcosta.mybooks.data.SaveResult
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.KClass

class BookDetailViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val dao = FakeBookDao()
    private val repo = BookRepository(dao, object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    }, clock = { 5000L })

    private suspend fun newBook(notes: String = "") =
        (repo.save(Book(title = "Dune", notes = notes)) as SaveResult.Saved).id

    /** Creates the ViewModel inside a store so the test can clear it like navigation does. */
    private fun TestScope.inStore(id: Long, saveScope: CoroutineScope = backgroundScope): Pair<BookDetailViewModel, ViewModelStore> {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                BookDetailViewModel(repo, id, saveScope) as T
        }
        return ViewModelProvider.create(store, factory)[BookDetailViewModel::class] to store
    }

    @Test
    fun loadsBookAndNotesDraft() = runTest {
        val id = newBook(notes = "first thoughts")
        val (vm, _) = inStore(id)
        assertEquals("Dune", vm.book.value?.title)
        assertEquals("first thoughts", vm.notesDraft.value)
    }

    @Test
    fun setStatusReadSetsDateRead() = runTest {
        val id = newBook()
        val (vm, _) = inStore(id)
        vm.setStatus(ReadingStatus.READ)
        assertEquals(ReadingStatus.READ, vm.book.value?.status)
        assertEquals(5000L, vm.book.value?.dateRead)
    }

    @Test
    fun rateAndClear() = runTest {
        val id = newBook()
        val (vm, _) = inStore(id)
        vm.rate(4)
        assertEquals(4, vm.book.value?.rating)
        vm.rate(null)
        assertNull(vm.book.value?.rating)
    }

    @Test
    fun saveNotesPersists() = runTest {
        val id = newBook()
        val (vm, _) = inStore(id, saveScope = this)
        vm.editNotes("brilliant")
        vm.saveNotes()
        advanceUntilIdle()
        assertEquals("brilliant", dao.getById(id)?.notes)
    }

    @Test
    fun notesSavedOnCleared() = runTest {
        val id = newBook()
        val (vm, store) = inStore(id, saveScope = this)
        vm.editNotes("typed then pressed back")
        store.clear()
        advanceUntilIdle()
        assertEquals("typed then pressed back", dao.getById(id)?.notes)
    }

    @Test
    fun notesChangedElsewhereRefreshDraftAndAreNotOverwritten() = runTest {
        val id = newBook(notes = "a")
        val (vm, store) = inStore(id, saveScope = this)
        repo.setNotes(id, "b") // e.g. edited on the Edit screen
        assertEquals("b", vm.notesDraft.value)
        store.clear()
        advanceUntilIdle()
        assertEquals("b", dao.getById(id)?.notes)
    }

    @Test
    fun unsavedDraftIsKeptWhenBookChangesElsewhere() = runTest {
        val id = newBook(notes = "a")
        val (vm, _) = inStore(id, saveScope = this)
        vm.editNotes("typing…")
        vm.rate(3)
        assertEquals("typing…", vm.notesDraft.value)
    }

    @Test
    fun deleteRemovesBook() = runTest {
        val id = newBook()
        val (vm, _) = inStore(id)
        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.deleted.value)
        assertNull(dao.getById(id))
    }

    @Test
    fun clearingAfterDeleteDoesNotRecreateBook() = runTest {
        val id = newBook()
        val (vm, store) = inStore(id, saveScope = this)
        vm.editNotes("x")
        vm.delete()
        store.clear()
        advanceUntilIdle()
        assertNull(dao.getById(id))
        assertNotNull(vm)
    }
}
