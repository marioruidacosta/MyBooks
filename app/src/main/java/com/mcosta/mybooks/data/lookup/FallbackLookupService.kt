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
        if (second is LookupResult.Found) return second
        val firstError = (first as? LookupResult.Error)?.cause
        val secondError = (second as? LookupResult.Error)?.cause
        return when {
            firstError != null && secondError != null -> LookupResult.Error(firstError)
            firstError != null -> LookupResult.NotFoundWithErrors(firstError)
            secondError != null -> LookupResult.NotFoundWithErrors(secondError)
            else -> LookupResult.NotFound
        }
    }
}
