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
        assertEquals(listOf("Ana", "a casa", "O Castelo", "Os Maias", "Ósmio", "The Road", "Zorba"), books.sortedFor(LibrarySort.TITLE).titles())
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
        assertEquals(listOf("Emma", "Os Maias", "Ensaio", "Memorial", "Anónimo"), books.sortedFor(LibrarySort.AUTHOR).titles())
    }

    @Test
    fun publisherThenTitleNoPublisherLast() {
        val books = listOf(book("B", publisher = "Relógio d'Água"), book("Z"), book("A", publisher = "Asa"), book("A2", publisher = "relogio D'Agua"))
        assertEquals(listOf("A", "A2", "B", "Z"), books.sortedFor(LibrarySort.PUBLISHER).titles())
    }

    @Test
    fun scoreHighestFirstUnratedLast() {
        val books = listOf(book("Three", rating = 3), book("None"), book("Five b", rating = 5), book("Five a", rating = 5), book("One", rating = 1))
        assertEquals(listOf("Five a", "Five b", "Three", "One", "None"), books.sortedFor(LibrarySort.SCORE).titles())
    }
}
