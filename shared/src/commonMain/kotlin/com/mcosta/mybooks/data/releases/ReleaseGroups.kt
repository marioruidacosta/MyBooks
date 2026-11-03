package com.mcosta.mybooks.data.releases

import com.mcosta.mybooks.domain.searchKey

/** The key of "Author not stated" / "Publisher not stated" (never a real name). */
const val NOT_STATED = "-"

/** One author or publisher of a month, with its number of books. [key] is what [ReleaseFilter] matches. */
data class NameCount(val key: String, val label: String, val count: Int)

/** Which of a month's books a list shows. */
sealed interface ReleaseFilter {
    data class Genre(val genre: ReleaseGenre) : ReleaseFilter
    data class Author(val key: String) : ReleaseFilter
    data class Publisher(val key: String) : ReleaseFilter
}

/** Cataloguers write "[s.n.]" (sine nomine) when the book names no publisher. */
private val NO_PUBLISHER = Regex("""\bs\.\s?n\.""", RegexOption.IGNORE_CASE)

/** The publisher as listed, or [NOT_STATED]; stray catalogue brackets are trimmed. */
fun ReleaseBook.publisherKey(): String {
    val name = publisher?.trim(' ', '[', ']')?.takeIf { it.isNotEmpty() } ?: return NOT_STATED
    return if (NO_PUBLISHER.containsMatchIn(name)) NOT_STATED else name
}

/** Every author of the book (a two-author book is listed under both), or [NOT_STATED]. */
fun ReleaseBook.authorKeys(): List<String> = authors.filter(String::isNotBlank).ifEmpty { listOf(NOT_STATED) }

fun ReleaseBook.matches(filter: ReleaseFilter): Boolean = when (filter) {
    is ReleaseFilter.Genre -> genreType == filter.genre
    is ReleaseFilter.Author -> filter.key in authorKeys()
    is ReleaseFilter.Publisher -> publisherKey() == filter.key
}

/** The month's authors A–Z (ignoring case and accents), "Author not stated" last. */
fun authorCounts(books: List<ReleaseBook>): List<NameCount> =
    counts(books.flatMap { it.authorKeys().distinct() }, "Author not stated")
        .sortedWith(compareBy({ it.key == NOT_STATED }, { searchKey(it.label) }))

/** The month's publishers, most books first (then A–Z), "Publisher not stated" last. */
fun publisherCounts(books: List<ReleaseBook>): List<NameCount> =
    counts(books.map { it.publisherKey() }, "Publisher not stated")
        .sortedWith(compareBy({ it.key == NOT_STATED }, { -it.count }, { searchKey(it.label) }))

/** The names containing [query], ignoring case and accents; everything for a blank query. */
fun List<NameCount>.search(query: String): List<NameCount> {
    val q = searchKey(query.trim())
    return if (q.isEmpty()) this else filter { searchKey(it.label).contains(q) }
}

private fun counts(keys: List<String>, notStatedLabel: String): List<NameCount> =
    keys.groupingBy { it }.eachCount().map { (key, n) -> NameCount(key, if (key == NOT_STATED) notStatedLabel else key, n) }
