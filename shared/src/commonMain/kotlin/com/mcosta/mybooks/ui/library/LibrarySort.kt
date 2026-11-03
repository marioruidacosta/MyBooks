package com.mcosta.mybooks.ui.library

import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.searchKey

/** How the library lists books; [SCORE] is offered on the Read tab only. [ascending]/[descending] name the directions. */
enum class LibrarySort(val label: String, val ascending: String, val descending: String) {
    TITLE("Title", "A to Z", "Z to A"),
    AUTHOR("Author", "A to Z", "Z to A"),
    PUBLISHER("Publisher", "A to Z", "Z to A"),
    SCORE("Score", "Lowest first", "Highest first"),
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

/** A tab's order and direction (A to Z / lowest first unless [descending]). */
data class SortChoice(val order: LibrarySort = LibrarySort.TITLE, val descending: Boolean = false) {
    /** How it's stored: "AUTHOR" or "AUTHOR:desc". */
    fun encode(): String = if (descending) "${order.name}:desc" else order.name

    companion object {
        fun decode(text: String?): SortChoice? {
            val order = LibrarySort.entries.firstOrNull { it.name == text?.substringBefore(':') } ?: return null
            return SortChoice(order, descending = text?.endsWith(":desc") == true)
        }
    }
}

/**
 * The books in [choice] order. Books without an author, publisher or score stay last in either direction;
 * ties go A to Z by title.
 */
fun List<Book>.sortedFor(choice: SortChoice): List<Book> {
    // Keys computed once per book, not per comparison.
    val keyed = map { Keyed(it, titleKey(it.title)) }
    val byTitle = compareBy<Keyed> { it.title }.thenBy { it.book.id }
    fun <T : Comparable<T>> by(key: (Keyed) -> T?): Comparator<Keyed> {
        val keys = keyed.associateWith(key)
        val primary = compareBy<Keyed> { keys[it] }.let { if (choice.descending) it.reversed() else it }
        return compareBy<Keyed> { keys[it] == null }.then(primary).then(byTitle)
    }
    val order = when (choice.order) {
        LibrarySort.TITLE -> if (choice.descending) compareByDescending<Keyed> { it.title }.thenBy { it.book.id } else byTitle
        LibrarySort.AUTHOR -> by { authorKey(it.book.authors) }
        LibrarySort.PUBLISHER -> by { it.book.publisher?.takeIf(String::isNotBlank)?.let(::searchKey) }
        LibrarySort.SCORE -> by { it.book.rating }
    }
    return keyed.sortedWith(order).map { it.book }
}

private class Keyed(val book: Book, val title: String)
