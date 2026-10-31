package com.mcosta.mybooks.ui.detail

import com.mcosta.mybooks.domain.Book

/** "Page 120 of 535 · 22%", "Page 120" or "22%"; null when no progress has been entered. */
fun progressText(book: Book): String? {
    val page = book.currentPage
    val percent = book.progressPercent
    return when {
        page != null && book.pageCount != null && percent != null -> "Page $page of ${book.pageCount} · $percent%"
        page != null -> "Page $page"
        percent != null -> "$percent%"
        else -> null
    }
}
