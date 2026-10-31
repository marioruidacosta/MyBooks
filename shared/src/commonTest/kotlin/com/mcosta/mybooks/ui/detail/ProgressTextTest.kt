package com.mcosta.mybooks.ui.detail

import com.mcosta.mybooks.domain.Book
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class ProgressTextTest {
    private val book = Book(title = "Dune")

    @Test fun pageWithCount() =
        assertEquals("Page 120 of 535 · 22%", progressText(book.copy(pageCount = 535, currentPage = 120, progressPercent = 22)))

    @Test fun pageWithoutCount() = assertEquals("Page 120", progressText(book.copy(currentPage = 120)))

    @Test fun percentOnly() = assertEquals("45%", progressText(book.copy(progressPercent = 45)))

    @Test fun noProgress() = assertNull(progressText(book))
}
