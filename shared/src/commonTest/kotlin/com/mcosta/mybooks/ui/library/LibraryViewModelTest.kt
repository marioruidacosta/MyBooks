package com.mcosta.mybooks.ui.library

import com.mcosta.mybooks.MainDispatcherTest
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.FakeBookDao
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import com.mcosta.mybooks.data.Preferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

class LibraryViewModelTest : MainDispatcherTest() {
    private val repo = BookRepository(FakeBookDao(), object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    })

    @Test
    fun tabAndQueryFilterBooks() = runTest {
        repo.save(Book(title = "Dune", authors = "Frank Herbert", status = ReadingStatus.READ))
        repo.save(Book(title = "Emma", authors = "Jane Austen", status = ReadingStatus.WISHLIST))
        repo.save(Book(title = "Persuasion", authors = "Jane Austen", status = ReadingStatus.READ))
        val vm = LibraryViewModel(repo)
        backgroundScope.launch(mainDispatcher) { vm.state.collect {} }

        assertEquals(listOf("Dune", "Emma", "Persuasion"), vm.state.value.books.map { it.title })

        vm.selectTab(ReadingStatus.READ)
        assertEquals(listOf("Dune", "Persuasion"), vm.state.value.books.map { it.title })

        vm.setQuery("austen")
        assertEquals(listOf("Persuasion"), vm.state.value.books.map { it.title })
        assertEquals(ReadingStatus.READ, vm.state.value.tab)
        assertEquals("austen", vm.state.value.query)

        vm.selectTab(null)
        assertEquals(listOf("Emma", "Persuasion"), vm.state.value.books.map { it.title })
    }

    private class MemoryPreferences : Preferences {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String) { values[key] = value }
    }

    @Test
    fun sortAppliesToAllTabsAndScoreOnlyToRead() = runTest {
        repo.save(Book(title = "Dune", authors = "Frank Herbert", publisher = "Ace", status = ReadingStatus.READ, rating = 3))
        repo.save(Book(title = "Emma", authors = "Jane Austen", publisher = "Penguin", status = ReadingStatus.WISHLIST))
        repo.save(Book(title = "Persuasion", authors = "Jane Austen", publisher = "Bantam", status = ReadingStatus.READ, rating = 5))
        val prefs = MemoryPreferences()
        val vm = LibraryViewModel(repo, prefs)
        backgroundScope.launch(mainDispatcher) { vm.state.collect {} }

        assertEquals(listOf(LibrarySort.TITLE, LibrarySort.AUTHOR, LibrarySort.PUBLISHER), vm.state.value.sorts)
        vm.setSort(LibrarySort.AUTHOR)
        assertEquals(listOf("Emma", "Persuasion", "Dune"), vm.state.value.books.map { it.title })
        vm.setSort(LibrarySort.SCORE)                                  // not offered outside Read: ignored
        assertEquals(LibrarySort.AUTHOR, vm.state.value.sort)

        vm.selectTab(ReadingStatus.READ)
        assertEquals(LibrarySort.TITLE, vm.state.value.sort)           // Read keeps its own choice
        assertEquals(LibrarySort.entries, vm.state.value.sorts)
        vm.setSort(LibrarySort.SCORE)
        assertEquals(listOf("Persuasion", "Dune"), vm.state.value.books.map { it.title })

        vm.selectTab(ReadingStatus.WISHLIST)
        assertEquals(LibrarySort.AUTHOR, vm.state.value.sort)

        // Remembered after the app is closed.
        val again = LibraryViewModel(repo, prefs)
        backgroundScope.launch(mainDispatcher) { again.state.collect {} }
        assertEquals(LibrarySort.AUTHOR, again.state.value.sort)
        again.selectTab(ReadingStatus.READ)
        assertEquals(LibrarySort.SCORE, again.state.value.sort)
    }

    @Test
    fun publisherSortWithSearch() = runTest {
        repo.save(Book(title = "Persuasion", authors = "Jane Austen", publisher = "Penguin"))
        repo.save(Book(title = "Emma", authors = "Jane Austen", publisher = "Bantam"))
        val vm = LibraryViewModel(repo)
        backgroundScope.launch(mainDispatcher) { vm.state.collect {} }
        vm.setSort(LibrarySort.PUBLISHER)
        vm.setQuery("austen")
        assertEquals(listOf("Emma", "Persuasion"), vm.state.value.books.map { it.title })
    }
}
