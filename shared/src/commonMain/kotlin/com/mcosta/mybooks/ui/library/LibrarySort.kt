package com.mcosta.mybooks.ui.library

import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.searchKey

/** How the library lists books; [SCORE] is offered on the Read tab only. */
enum class LibrarySort(val label: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    PUBLISHER("Publisher"),
    SCORE("Score"),
}

/** Leading articles (Portuguese and English) that titles aren't sorted by: "O Castelo" is under C. */
private val ARTICLE = Regex("""^(o|a|os|as|um|uma|the|an)\s+""")

/** Ignores case, accents, leading punctuation (quotes, «) and a leading article. */
internal fun titleKey(title: String): String =
    searchKey(title.trim()).trimStart { !it.isLetterOrDigit() }.replace(ARTICLE, "")

/** The first author by surname (the last word: "José Saramago" under S), or null without one. */
internal fun authorKey(authors: String): String? {
    val first = authors.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    return searchKey("${first.substringAfterLast(' ')} $first")
}

/** The books in [sort] order; ties (and books without an author, publisher or score, listed last) go by title. */
fun List<Book>.sortedFor(sort: LibrarySort): List<Book> {
    // Keys computed once per book, not per comparison.
    val keyed = map { Keyed(it, titleKey(it.title)) }
    val byTitle = compareBy<Keyed> { it.title }.thenBy { it.book.id }
    val order = when (sort) {
        LibrarySort.TITLE -> byTitle
        LibrarySort.AUTHOR -> keyed.associateWith { authorKey(it.book.authors) }.let { k ->
            compareBy<Keyed> { k[it] == null }.thenBy { k[it] }.then(byTitle)
        }
        LibrarySort.PUBLISHER -> keyed.associateWith { it.book.publisher?.takeIf(String::isNotBlank)?.let(::searchKey) }.let { k ->
            compareBy<Keyed> { k[it] == null }.thenBy { k[it] }.then(byTitle)
        }
        LibrarySort.SCORE -> compareBy<Keyed> { it.book.rating == null }.thenByDescending { it.book.rating }.then(byTitle)
    }
    return keyed.sortedWith(order).map { it.book }
}

private class Keyed(val book: Book, val title: String)
