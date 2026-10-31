package com.mcosta.mybooks.data.lookup

import com.mcosta.mybooks.domain.Book
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

class OpenLibraryClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://openlibrary.org",
    private val coversBaseUrl: String = "https://covers.openlibrary.org",
) : BookLookupService {

    override suspend fun lookup(isbn: String): LookupResult = lookupCatching {
        val body = http.getOrNull("$baseUrl/isbn/$isbn.json") ?: return LookupResult.NotFound
        val edition = lookupJson.decodeFromString<Edition>(body)
        val title = edition.title?.let(::cleanText)?.takeIf { it.isNotBlank() } ?: return LookupResult.NotFound
        LookupResult.Found(
            Book(
                isbn = isbn,
                title = title,
                authors = edition.authors.mapNotNull { authorName(it.key)?.let(::cleanText) }.joinToString(", "),
                publisher = edition.publishers.firstOrNull()?.let(::cleanText),
                publishedYear = yearFrom(edition.publish_date),
                pageCount = edition.number_of_pages,
                // Only real covers: a cover-by-ISBN URL returns a blank 1x1 GIF when there is none.
                coverUrl = edition.covers.firstOrNull { it > 0 }?.let { "$coversBaseUrl/b/id/$it-L.jpg" },
            )
        )
    }

    /** Author names are best-effort: a failed author request just leaves that name out. */
    private suspend fun authorName(key: String): String? = try {
        http.getOrNull("$baseUrl$key.json")?.let { lookupJson.decodeFromString<Author>(it).name }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    @Suppress("PropertyName")
    @Serializable
    private data class Edition(
        val title: String? = null,
        val authors: List<AuthorRef> = emptyList(),
        val publishers: List<String> = emptyList(),
        val publish_date: String? = null,
        val number_of_pages: Int? = null,
        val covers: List<Long> = emptyList(),
    )

    @Serializable
    private data class AuthorRef(val key: String)

    @Serializable
    private data class Author(val name: String? = null)
}
