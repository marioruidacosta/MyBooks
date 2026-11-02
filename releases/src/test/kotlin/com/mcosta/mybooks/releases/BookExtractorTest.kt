package com.mcosta.mybooks.releases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BookExtractorTest {
    private fun book(id: String) = assertNotNull(BookExtractor.extract(Fixtures.record(id)), "record $id should be a new book")

    @Test
    fun extractsFields() {
        val b = book("2293200")
        assertEquals("9789895682799", b.isbn)
        assertEquals("A casa de cal", b.title)
        assertEquals(listOf("Thomas Bernhard"), b.authors)
        assertEquals("Documenta", b.publisher)
        assertEquals("Lisboa", b.place)
        assertEquals(2026, b.year)
        assertEquals("http://id.bnportugal.gov.pt/bib/catbnp/2293200", b.link)
    }

    @Test
    fun translatorsHaveRole730() {
        val b = book("2290561")
        assertEquals(listOf("Marie Vareille"), b.authors)
        assertEquals(listOf("Helder Guégués"), b.translators)
    }

    @Test
    fun titleIncludesSubtitle() =
        assertEquals("A química das emoções: uma viagem molecular pelo que sentimos", book("2291160").title)

    @Test
    fun titleSpacesAndClosingFullStopTidied() {
        assertEquals("Os túmulos de Atuan", book("2290897").title)
        assertEquals("Deuses do jogo", book("2291019").title)
    }

    @Test
    fun authorsFallBackToStatement() {
        val r = record(
            fields = arrayOf(
                field("010", 'a' to "978-989-568-279-9"),
                field("200", 'a' to "Antologia", 'f' to "org. Ana Lima "),
                field("210", 'a' to "Porto", 'c' to "Afrontamento", 'd' to "2026"),
                field("702", 'a' to "Lima", 'b' to "Ana", '4' to "340"),
            ),
        )
        val b = assertNotNull(BookExtractor.extract(r))
        assertEquals(listOf("org. Ana Lima"), b.authors)
        assertEquals(emptyList(), b.translators)
    }

    @Test
    fun skipsEditedOldRecord() = assertNull(BookExtractor.extract(Fixtures.record("1154749")))

    @Test
    fun skipsWithoutIsbn() = assertNull(BookExtractor.extract(Fixtures.record("2292843")))

    @Test
    fun skipsPeriodical() = assertNull(BookExtractor.extract(Fixtures.record("1943069")))

    @Test
    fun incompleteRecordIsSkipped() {
        val noImprint = record(fields = arrayOf(field("010", 'a' to "978-989-568-279-9"), field("200", 'a' to "Sem editor")))
        assertNull(BookExtractor.extract(noImprint))
        val noTitle = record(fields = arrayOf(field("010", 'a' to "978-989-568-279-9"), field("210", 'd' to "2026")))
        assertNull(BookExtractor.extract(noTitle))
    }

    @Test
    fun yearBeforeIsAccepted() = assertEquals(2025, book("2290983").year)

    @Test
    fun copyrightYearIsRead() = assertEquals(2025, book("2218820").year)

    @Test
    fun genreOfFixtureBooks() {
        assertEquals("novel", book("2293200").genre)
        assertEquals("children", book("2291280").genre)
        assertEquals("history", book("2290698").genre)
    }

    @Test
    fun isbdPunctuationTrimmed() {
        val r = record(
            fields = arrayOf(
                field("010", 'a' to "978-989-568-279-9"),
                field("200", 'a' to "An unending bridge :", 'e' to "the Azorean Islands /"),
                field("210", 'a' to "Ponta Delgada :", 'c' to "Letras Lavadas ;", 'd' to "2026"),
            ),
        )
        val b = assertNotNull(BookExtractor.extract(r))
        assertEquals("Letras Lavadas", b.publisher)
        assertEquals("Ponta Delgada", b.place)
        assertEquals("An unending bridge: the Azorean Islands", b.title)
    }
}
