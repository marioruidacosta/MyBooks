package com.mcosta.mybooks.ui.library

import com.mcosta.mybooks.domain.Book
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortTest {
    private var nextId = 1L
    private fun book(title: String, authors: String = "", publisher: String? = null, rating: Int? = null) =
        Book(id = nextId++, title = title, authors = authors, publisher = publisher, rating = rating)

    private fun List<Book>.titles() = map { it.title }

    @Test
    fun titleIgnoresCaseAccentsAndLeadingArticles() {
        val books = listOf(book("Zorba"), book("O Castelo"), book("Ósmio"), book("a casa"), book("The Road"), book("Os Maias"), book("Ana"))
        assertEquals(listOf("Ana", "a casa", "O Castelo", "Os Maias", "Ósmio", "The Road", "Zorba"), books.sortedFor(SortChoice(LibrarySort.TITLE)).titles())
    }

    @Test
    fun authorBySurnameThenTitleNoAuthorLast() {
        val books = listOf(
            book("Memorial", "José Saramago"),
            book("Anónimo"),
            book("Os Maias", "Eça de Queirós"),
            book("Ensaio", "José Saramago, Outro Nome"),
            book("Emma", "Jane Austen"),
        )
        assertEquals(listOf("Emma", "Os Maias", "Ensaio", "Memorial", "Anónimo"), books.sortedFor(SortChoice(LibrarySort.AUTHOR)).titles())
    }

    @Test
    fun publisherThenTitleNoPublisherLast() {
        val books = listOf(book("B", publisher = "Relógio d'Água"), book("Z"), book("A", publisher = "Asa"), book("A2", publisher = "relogio D'Agua"))
        assertEquals(listOf("A", "A2", "B", "Z"), books.sortedFor(SortChoice(LibrarySort.PUBLISHER)).titles())
    }

    @Test
    fun scoreHighestFirstUnratedLast() {
        val books = listOf(book("Three", rating = 3), book("None"), book("Five b", rating = 5), book("Five a", rating = 5), book("One", rating = 1))
        assertEquals(listOf("Five a", "Five b", "Three", "One", "None"), books.sortedFor(SortChoice(LibrarySort.SCORE, descending = true)).titles())
    }

    @Test
    fun descendingReversesButMissingValuesStayLast() {
        val books = listOf(book("Mid", "Ana Bela", "Bertrand", 3), book("Low", "Zé Zorro", "Asa", 1), book("Bare"), book("High", "Carla Costa", "Porto", 5))
        assertEquals(listOf("Mid", "Low", "High", "Bare"), books.sortedFor(SortChoice(LibrarySort.TITLE, descending = true)).titles())
        assertEquals(listOf("Low", "High", "Mid", "Bare"), books.sortedFor(SortChoice(LibrarySort.AUTHOR, descending = true)).titles())
        assertEquals(listOf("High", "Mid", "Low", "Bare"), books.sortedFor(SortChoice(LibrarySort.PUBLISHER, descending = true)).titles())
        assertEquals(listOf("Low", "Mid", "High", "Bare"), books.sortedFor(SortChoice(LibrarySort.SCORE)).titles())
    }

    @Test
    fun choiceIsStoredAndReadBack() {
        val choice = SortChoice(LibrarySort.PUBLISHER, descending = true)
        assertEquals(choice, SortChoice.decode(choice.encode()))
        assertEquals(SortChoice(LibrarySort.AUTHOR), SortChoice.decode("AUTHOR"))
        assertEquals(null, SortChoice.decode("SOMETHING"))
        assertEquals(null, SortChoice.decode(null))
    }
}
