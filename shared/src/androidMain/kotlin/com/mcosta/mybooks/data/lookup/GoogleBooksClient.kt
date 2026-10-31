package com.mcosta.mybooks.data.lookup

import com.mcosta.mybooks.domain.Book
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient

class GoogleBooksClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://www.googleapis.com",
    /** Without a key, requests share Google's anonymous daily quota, which is usually exhausted (HTTP 429). */
    private val apiKey: String? = null,
) : BookLookupService {

    override suspend fun lookup(isbn: String): LookupResult = lookupCatching {
        val keyParam = apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let { "&key=$it" }.orEmpty()
        val body = http.getOrNull("$baseUrl/books/v1/volumes?q=isbn:$isbn$keyParam") ?: return LookupResult.NotFound
        val info = lookupJson.decodeFromString<Volumes>(body).items.firstOrNull()?.volumeInfo
            ?: return LookupResult.NotFound
        val title = listOfNotNull(info.title, info.subtitle).map(::cleanText).filter { it.isNotEmpty() }.joinToString(": ")
        if (title.isBlank()) return LookupResult.NotFound
        LookupResult.Found(
            Book(
                isbn = isbn,
                title = title,
                authors = info.authors.map(::cleanText).filter { it.isNotEmpty() }.joinToString(", "),
                publisher = info.publisher?.let(::cleanText)?.ifEmpty { null },
                publishedYear = info.publishedDate?.take(4)?.toIntOrNull(),
                pageCount = info.pageCount,
                coverUrl = httpsUrl(info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail),
                description = info.description,
            )
        )
    }

    @Serializable
    private data class Volumes(val items: List<Item> = emptyList())

    @Serializable
    private data class Item(val volumeInfo: VolumeInfo? = null)

    @Serializable
    private data class VolumeInfo(
        val title: String? = null,
        val subtitle: String? = null,
        val authors: List<String> = emptyList(),
        val publisher: String? = null,
        val publishedDate: String? = null,
        val description: String? = null,
        val pageCount: Int? = null,
        val imageLinks: ImageLinks? = null,
    )

    @Serializable
    private data class ImageLinks(val smallThumbnail: String? = null, val thumbnail: String? = null)
}
