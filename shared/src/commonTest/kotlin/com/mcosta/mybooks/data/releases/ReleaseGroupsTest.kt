package com.mcosta.mybooks.data.releases

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseGroupsTest {
    private fun book(id: String, authors: List<String> = emptyList(), publisher: String? = null) =
        ReleaseBook(id = id, isbn = "9789895682799", title = "T$id", authors = authors, publisher = publisher, genre = "novel")

    private val books = listOf(
        book("1", listOf("Zé Álvaro", "Ana Lima"), "Presença"),
        book("2", listOf("Ana Lima"), "Presença"),
        book("3", listOf("Óscar Neves"), "Asa"),
        book("4", emptyList(), "s.n.]"),
        book("5", listOf("Bruno Reis"), "[S.l. : s.n.]"),
        book("6", listOf("Ana Lima"), null),
        book("7", listOf("Carla Dias"), "Asa"),
        book("8", listOf("Carla Dias"), "Presença"),
    )

    @Test
    fun authorsAtoZIgnoringAccentsWithNotStatedLast() {
        assertEquals(
            listOf(
                NameCount("Ana Lima", "Ana Lima", 3), NameCount("Bruno Reis", "Bruno Reis", 1),
                NameCount("Carla Dias", "Carla Dias", 2), NameCount("Óscar Neves", "Óscar Neves", 1),
                NameCount("Zé Álvaro", "Zé Álvaro", 1), NameCount(NOT_STATED, "Author not stated", 1),
            ),
            authorCounts(books),
        )
    }

    @Test
    fun publishersMostBooksFirstWithNotStatedLast() {
        assertEquals(
            listOf(NameCount("Presença", "Presença", 3), NameCount("Asa", "Asa", 2), NameCount(NOT_STATED, "Publisher not stated", 3)),
            publisherCounts(books),
        )
    }

    @Test
    fun twoAuthorBookListedUnderBoth() {
        assertEquals(listOf("1", "2", "6"), books.filter { it.matches(ReleaseFilter.Author("Ana Lima")) }.map { it.id })
        assertEquals(listOf("1"), books.filter { it.matches(ReleaseFilter.Author("Zé Álvaro")) }.map { it.id })
    }

    @Test
    fun filtersForPublisherAndNotStated() {
        assertEquals(listOf("3", "7"), books.filter { it.matches(ReleaseFilter.Publisher("Asa")) }.map { it.id })
        assertEquals(listOf("4", "5", "6"), books.filter { it.matches(ReleaseFilter.Publisher(NOT_STATED)) }.map { it.id })
        assertEquals(listOf("4"), books.filter { it.matches(ReleaseFilter.Author(NOT_STATED)) }.map { it.id })
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf("Óscar Neves"), authorCounts(books).search("oscar").map { it.label })
        assertEquals(listOf("Presença"), publisherCounts(books).search("PRESENCA").map { it.label })
        assertEquals(6, authorCounts(books).search("  ").size)
    }
}
