package com.mcosta.mybooks.data.lookup

/**
 * Asks [sources] in order and returns the first book found. If that book has no cover, the remaining
 * sources are asked only for a cover, which is added to it.
 */
class LookupChain(private val sources: List<BookLookupService>) : BookLookupService {

    override suspend fun lookup(isbn: String): LookupResult {
        var firstError: Throwable? = null
        var anyMiss = false
        for ((index, source) in sources.withIndex()) {
            when (val result = source.lookup(isbn)) {
                is LookupResult.Found ->
                    return if (result.book.coverUrl != null) result else withCoverFrom(sources.drop(index + 1), isbn, result)
                LookupResult.NotFound -> anyMiss = true
                is LookupResult.NotFoundWithErrors -> { anyMiss = true; firstError = firstError ?: result.cause }
                is LookupResult.Error -> firstError = firstError ?: result.cause
            }
        }
        val error = firstError
        return when {
            error == null -> LookupResult.NotFound
            anyMiss -> LookupResult.NotFoundWithErrors(error)
            else -> LookupResult.Error(error)
        }
    }

    private suspend fun withCoverFrom(rest: List<BookLookupService>, isbn: String, found: LookupResult.Found): LookupResult.Found {
        for (source in rest) {
            val cover = (source.lookup(isbn) as? LookupResult.Found)?.book?.coverUrl
            if (cover != null) return LookupResult.Found(found.book.copy(coverUrl = cover))
        }
        return found
    }
}
