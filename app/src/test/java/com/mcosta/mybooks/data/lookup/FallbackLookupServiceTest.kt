package com.mcosta.mybooks.data.lookup

import com.mcosta.mybooks.domain.Book
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FallbackLookupServiceTest {
    private class Fixed(val result: LookupResult) : BookLookupService {
        var calls = 0
        override suspend fun lookup(isbn: String): LookupResult { calls++; return result }
    }

    private val found = LookupResult.Found(Book(title = "Dune"))
    private val found2 = LookupResult.Found(Book(title = "Dune (OL)"))
    private val error = LookupResult.Error(IOException("offline"))

    @Test
    fun primaryHitSkipsSecondary() = runTest {
        val secondary = Fixed(found2)
        assertEquals(found, FallbackLookupService(Fixed(found), secondary).lookup("x"))
        assertEquals(0, secondary.calls)
    }

    @Test
    fun primaryMissUsesSecondary() = runTest {
        assertEquals(found2, FallbackLookupService(Fixed(LookupResult.NotFound), Fixed(found2)).lookup("x"))
    }

    @Test
    fun bothMissNotFound() = runTest {
        assertEquals(
            LookupResult.NotFound,
            FallbackLookupService(Fixed(LookupResult.NotFound), Fixed(LookupResult.NotFound)).lookup("x"),
        )
    }

    @Test
    fun primaryErrorSecondaryFound() = runTest {
        assertEquals(found2, FallbackLookupService(Fixed(error), Fixed(found2)).lookup("x"))
    }

    @Test
    fun primaryErrorSecondaryMissIsError() = runTest {
        assertTrue(FallbackLookupService(Fixed(error), Fixed(LookupResult.NotFound)).lookup("x") is LookupResult.Error)
    }
}
