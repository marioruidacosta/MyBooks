package com.mcosta.mybooks.ui.social

import com.mcosta.mybooks.data.BookRepository
import com.mcosta.mybooks.data.SaveResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import com.mcosta.mybooks.social.ActivityService
import com.mcosta.mybooks.social.model.FeedBook
import com.mcosta.mybooks.social.model.FeedEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "Add to my books" for a feed entry's book, shared by the Activity and Profile screens. */
internal class LibraryShelf(private val repository: BookRepository) {
    enum class Added { ADDED, ALREADY_THERE, FAILED }

    /** Matched by ISBN, or by title and authors when there is none. */
    suspend fun contains(book: FeedBook): Boolean =
        (if (book.isbn != null) repository.findByIsbn(book.isbn) else repository.findByTitleAndAuthors(book.title, book.authors)) != null

    suspend fun add(book: FeedBook, status: ReadingStatus): Added {
        if (contains(book)) return Added.ALREADY_THERE
        val result = repository.save(
            Book(isbn = book.isbn, title = book.title, authors = book.authors, coverUrl = book.coverUrl, status = status)
        )
        return if (result is SaveResult.Saved) Added.ADDED else Added.FAILED
    }

    companion object {
        fun message(added: Added, status: ReadingStatus): String? = when (added) {
            Added.ADDED -> if (status == ReadingStatus.WISHLIST) "Added to To buy" else "Added to To read"
            Added.ALREADY_THERE -> null
            Added.FAILED -> "Couldn't add the book"
        }
    }
}

/**
 * The heart: flips at once, and flips back (with [onError]) if the server call fails. Taps on an entry whose
 * call is still in flight are ignored, so a quick double-tap can't leave the server opposite to the screen.
 * Used from the main thread only.
 */
internal class LikeToggler(private val activity: ActivityService, private val scope: CoroutineScope) {
    private val inFlight = mutableSetOf<Long>()

    /** [update] applies a change to the shown entry with that id. */
    fun toggle(entry: FeedEntry, update: (Long, (FeedEntry) -> FeedEntry) -> Unit, onError: (Exception) -> Unit) {
        if (!inFlight.add(entry.id)) return
        val liking = !entry.likedByMe
        update(entry.id) { it.copy(likedByMe = liking, likeCount = (it.likeCount + if (liking) 1 else -1).coerceAtLeast(0)) }
        scope.launch {
            try {
                if (liking) activity.like(entry.id) else activity.unlike(entry.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update(entry.id) { it.copy(likedByMe = entry.likedByMe, likeCount = entry.likeCount) }
                onError(e)
            } finally {
                inFlight.remove(entry.id)
            }
        }
    }
}
