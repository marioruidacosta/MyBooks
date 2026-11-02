package com.mcosta.mybooks.share

import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.social.model.PublicBookState

/** What the community sees of this book. Cover photos taken in the app are local files, so they're left out. */
fun Book.publicState(shareId: String) = PublicBookState(
    shareId = shareId,
    isbn = isbn,
    title = title,
    authors = authors,
    coverUrl = coverUrl?.takeIf { it.startsWith("https://") },
    status = status,
    rating = rating,
    progress = progressPercent,
    review = review,
)

/** Whether anything the community sees differs (notes, dates and the like don't count). */
fun publicFieldsDiffer(a: Book, b: Book): Boolean =
    a.status != b.status || a.rating != b.rating || a.progressPercent != b.progressPercent || a.review != b.review ||
        a.title != b.title || a.authors != b.authors || a.coverUrl != b.coverUrl
