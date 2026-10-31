package com.mcosta.mybooks.data.lookup

import com.mcosta.mybooks.domain.Book
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LookupChainTest {
    private class Fixed(val result: LookupResult) : BookLookupService {
        var calls = 0
        override suspend fun lookup(isbn: String): LookupResult { calls++; return result }
    }

    private val withCover = LookupResult.Found(Book(title = "Dune", coverUrl = "https://bnp/cover.jpg"))
    private val noCover = LookupResult.Found(Book(title = "Dune", authors = "Frank Herbert"))
    private val otherWithCover = LookupResult.Found(Book(title = "Dune (Google)", coverUrl = "https://google/cover.jpg"))
    private val error = LookupResult.Error(IOException("offline"))
    private val miss = LookupResult.NotFound

    @Test
    fun firstHitWithCoverSkipsTheRest() = runTest {
        val second = Fixed(otherWithCover)
        assertEquals(withCover, LookupChain(listOf(Fixed(withCover), second)).lookup("x"))
        assertEquals(0, second.calls)
    }

    @Test
    fun missesFallThroughInOrder() = runTest {
        val third = Fixed(otherWithCover)
        assertEquals(otherWithCover, LookupChain(listOf(Fixed(miss), Fixed(error), third)).lookup("x"))
        assertEquals(1, third.calls)
    }

    @Test
    fun hitWithoutCoverTakesCoverFromLaterSource() = runTest {
        val result = LookupChain(listOf(Fixed(noCover), Fixed(miss), Fixed(otherWithCover))).lookup("x")
        val book = (result as LookupResult.Found).book
        assertEquals("Dune", book.title)
        assertEquals("Frank Herbert", book.authors)
        assertEquals("https://google/cover.jpg", book.coverUrl)
    }

    @Test
    fun hitWithoutCoverStaysWithoutWhenNobodyHasOne() = runTest {
        assertEquals(noCover, LookupChain(listOf(Fixed(noCover), Fixed(error), Fixed(noCover))).lookup("x"))
    }

    @Test
    fun allMissIsNotFound() = runTest {
        assertEquals(miss, LookupChain(listOf(Fixed(miss), Fixed(miss), Fixed(miss))).lookup("x"))
    }

    @Test
    fun missAndErrorIsNotFoundWithErrors() = runTest {
        assertEquals(
            LookupResult.NotFoundWithErrors(error.cause),
            LookupChain(listOf(Fixed(miss), Fixed(error), Fixed(miss))).lookup("x"),
        )
    }

    @Test
    fun allErrorIsError() = runTest {
        assertTrue(LookupChain(listOf(Fixed(error), Fixed(error))).lookup("x") is LookupResult.Error)
    }
}
