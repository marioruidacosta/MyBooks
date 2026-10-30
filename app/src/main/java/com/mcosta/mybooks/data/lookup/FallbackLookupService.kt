package com.mcosta.mybooks.data.lookup

/** Tries [primary]; if it doesn't find the book (or fails), tries [secondary]. */
class FallbackLookupService(
    private val primary: BookLookupService,
    private val secondary: BookLookupService,
) : BookLookupService {

    override suspend fun lookup(isbn: String): LookupResult {
        val first = primary.lookup(isbn)
        if (first is LookupResult.Found) return first
        val second = secondary.lookup(isbn)
        return when {
            second is LookupResult.Found -> second
            first is LookupResult.Error -> first
            else -> second
        }
    }
}
