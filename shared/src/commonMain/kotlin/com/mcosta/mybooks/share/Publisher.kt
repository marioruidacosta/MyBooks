package com.mcosta.mybooks.share

import com.mcosta.mybooks.data.db.ShareStateDao
import com.mcosta.mybooks.data.db.ShareStateEntity
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.social.ActivityService
import com.mcosta.mybooks.social.SocialException
import com.mcosta.mybooks.social.model.PublicBookState
import com.mcosta.mybooks.social.supabase.logSocialError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun randomShareId() = Uuid.random().toString()

/**
 * Uploads the books waiting in share_state. Runs [debounce] after the last change (dragging the progress
 * slider sends one update), when sharing becomes active (app start, sign-in), and is retried the next time
 * after a failure: a failed upload leaves its book waiting.
 */
class Publisher(
    private val dao: ShareStateDao,
    private val loadBook: suspend (Long) -> Book?,
    private val activity: ActivityService,
    private val activeOwner: () -> String?,
    private val scope: CoroutineScope,
    private val debounce: Duration = 2.seconds,
    private val newShareId: () -> String = ::randomShareId,
) {
    private var timer: Job? = null
    private val running = Mutex()

    fun requestSync() {
        timer?.cancel()
        // The upload runs in its own job, so a later request restarts the timer without cancelling it.
        timer = scope.launch {
            delay(debounce)
            scope.launch { syncNow() }
        }
    }

    suspend fun syncNow() = running.withLock {
        val owner = activeOwner() ?: return@withLock
        dao.adoptOrphans(owner)
        for (waiting in dao.pendingFor(owner)) {
            val row = dao.get(waiting.bookId)?.takeIf { it.pending && it.owner == owner } ?: continue
            try {
                upload(row)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocialException) {
                if (e.kind == SocialException.Kind.NOT_AVAILABLE) {
                    giveNewShareId(row)
                    continue
                }
                return@withLock
            } catch (e: Exception) {
                logSocialError(e)
                return@withLock
            }
        }
    }

    private suspend fun upload(row: ShareStateEntity) {
        val book = loadBook(row.bookId)
        when {
            row.deleted || book == null -> {
                activity.unpublish(row.shareId)
                if (dao.get(row.bookId) == row) dao.delete(row.bookId)
            }
            row.excluded -> {
                activity.unpublish(row.shareId)
                if (dao.get(row.bookId) == row) dao.upsert(row.copy(pending = false))
            }
            else -> {
                val sent = book.publicState(row.shareId)
                activity.publish(sent, row.before(book))
                if (dao.get(row.bookId) != row) return
                // The server has the book now, so "before" is done with. Edited during the upload: send again.
                val unchanged = loadBook(row.bookId)?.publicState(row.shareId) == sent
                dao.upsert(row.withoutBefore().copy(pending = !unchanged))
            }
        }
    }

    /** The shared id belongs to another account (e.g. a backup restored by someone else): start afresh. */
    private suspend fun giveNewShareId(row: ShareStateEntity) {
        val book = loadBook(row.bookId) ?: return
        dao.upsert(
            row.copy(
                shareId = newShareId(), hasBefore = true, beforeStatus = book.status, beforeRating = book.rating,
                beforeProgress = book.progressPercent, beforeReview = book.review,
            )
        )
    }

    private fun ShareStateEntity.before(book: Book): PublicBookState? {
        val status = beforeStatus ?: return null
        if (!hasBefore) return null
        return book.publicState(shareId).copy(
            status = status, rating = beforeRating, progress = beforeProgress, review = beforeReview.orEmpty(),
        )
    }

    private fun ShareStateEntity.withoutBefore() =
        copy(hasBefore = false, beforeStatus = null, beforeRating = null, beforeProgress = null, beforeReview = null)
}
