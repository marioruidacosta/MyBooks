package com.mcosta.mybooks.ui.library

import com.mcosta.mybooks.MainDispatcherRule
import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.FakeBookDao
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = BookRepository(FakeBookDao(), object : BookLookupService {
        override suspend fun lookup(isbn: String): LookupResult = LookupResult.NotFound
    })

    @Test
    fun tabAndQueryFilterBooks() = runTest {
        repo.save(Book(title = "Dune", authors = "Frank Herbert", status = ReadingStatus.READ))
        repo.save(Book(title = "Emma", authors = "Jane Austen", status = ReadingStatus.WISHLIST))
        repo.save(Book(title = "Persuasion", authors = "Jane Austen", status = ReadingStatus.READ))
        val vm = LibraryViewModel(repo)
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect {} }

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
}
