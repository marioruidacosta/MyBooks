package com.mcosta.mybooks.share

import com.mcosta.mybooks.data.backup.BackupShare
import com.mcosta.mybooks.data.db.ShareStateDao
import com.mcosta.mybooks.data.db.ShareStateEntity
import com.mcosta.mybooks.domain.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun randomShareId() = Uuid.random().toString()

/**
 * Told about every library change; marks the books whose public state changed as waiting to upload.
 * The uploading itself is the Publisher's job ([onPending] asks it to run).
 */
class ShareTracker(
    private val dao: ShareStateDao,
    /** The signed-in user while sharing is on (has a profile and "Share my reading activity"); else null. */
    private val activeOwner: () -> String?,
    private val onPending: () -> Unit,
    private val newShareId: () -> String = ::randomShareId,
) {
    suspend fun changed(old: Book?, new: Book) {
        val owner = activeOwner() ?: return
        if (old != null && !publicFieldsDiffer(old, new)) return
        // Rows restored from a backup while signed out (owner "") are adopted; another account's are replaced.
        val row = dao.get(new.id)?.takeIf { it.owner == owner || it.owner == "" }?.copy(owner = owner)
        if (row?.excluded == true) return
        val base = row ?: newRow(new.id, owner)
        // "Before" is the state before this batch of changes; the server uses it only if it has no row yet.
        val marked = if (base.pending) base else base.withBefore(old)
        dao.upsert(marked.copy(pending = true))
        onPending()
    }

    suspend fun deleted(book: Book) {
        val row = dao.get(book.id) ?: return
        dao.upsert(row.copy(deleted = true, pending = true))
        onPending()
    }

    suspend fun setExcluded(book: Book, excluded: Boolean) {
        val row = dao.get(book.id)
        if (excluded) {
            val base = row ?: newRow(book.id, activeOwner() ?: "").withBefore(book)
            dao.upsert(base.copy(excluded = true, pending = true))
            onPending()
        } else if (row != null) {
            // Still waiting to be removed from the server: it never was, so it simply stays shared.
            if (row.pending) dao.upsert(row.copy(excluded = false, pending = false)) else dao.delete(book.id)
        }
    }

    fun observeExcluded(bookId: Long): Flow<Boolean> = dao.observe(bookId).map { it?.excluded == true }

    suspend fun exportShares(): Map<Long, BackupShare> =
        dao.all().filter { !it.deleted }.associate { it.bookId to BackupShare(it.shareId, it.excluded) }

    /** A book restored from a backup: same shared id, nothing to upload. */
    suspend fun restored(bookId: Long, share: BackupShare) {
        dao.upsert(newRow(bookId, activeOwner() ?: "").copy(shareId = share.shareId, excluded = share.excluded))
    }

    /** Sharing was turned off: the server removed everything, so the local state goes too. */
    suspend fun forgetOwner(owner: String) = dao.deleteForOwner(owner)

    private fun newRow(bookId: Long, owner: String) = ShareStateEntity(
        bookId = bookId, shareId = newShareId(), owner = owner, hasBefore = false,
        beforeStatus = null, beforeRating = null, beforeProgress = null, beforeReview = null,
        pending = false, excluded = false, deleted = false,
    )

    private fun ShareStateEntity.withBefore(book: Book?) = copy(
        hasBefore = book != null,
        beforeStatus = book?.status,
        beforeRating = book?.rating,
        beforeProgress = book?.progressPercent,
        beforeReview = book?.review,
    )
}
