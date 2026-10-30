package com.mcosta.mybooks.data.lookup

import com.mcosta.mybooks.domain.Book

/** Finds book metadata for an ISBN-13. */
interface BookLookupService {
    suspend fun lookup(isbn: String): LookupResult
}

sealed interface LookupResult {
    data class Found(val book: Book) : LookupResult
    data object NotFound : LookupResult
    data class Error(val cause: Throwable) : LookupResult
}
