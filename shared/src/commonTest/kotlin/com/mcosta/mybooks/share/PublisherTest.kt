@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mcosta.mybooks.share

import com.mcosta.mybooks.data.FakeBookDao
import com.mcosta.mybooks.data.FakeShareStateDao
import com.mcosta.mybooks.data.db.ShareStateEntity
import com.mcosta.mybooks.data.db.toBook
import com.mcosta.mybooks.data.db.toEntity
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import com.mcosta.mybooks.social.FakeSocial
import com.mcosta.mybooks.social.SocialException
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class PublisherTest {
    private val fake = FakeSocial()
    private val ana = fake.addUser("ana")
    private val books = FakeBookDao()
    private val shares = FakeShareStateDao()
    private var owner: String? = ana
    private var ids = 0

    private fun TestScope.publisher() = Publisher(
        shares, loadBook = { books.getById(it)?.toBook() }, activity = fake.activityService,
        activeOwner = { owner }, scope = backgroundScope, newShareId = { "new-${++ids}" },
    )

    private suspend fun book(title: String, status: ReadingStatus = ReadingStatus.READ, rating: Int? = 4): Long =
        books.insert(Book(title = title, status = status, rating = rating).toEntity())

    private suspend fun pendingRow(
        bookId: Long, shareId: String = "s$bookId", rowOwner: String = ana, before: ReadingStatus? = ReadingStatus.READ,
        excluded: Boolean = false, deleted: Boolean = false,
    ) = ShareStateEntity(
        bookId, shareId, rowOwner, hasBefore = before != null, beforeStatus = before, beforeRating = null,
        beforeProgress = null, beforeReview = "", pending = true, excluded = excluded, deleted = deleted,
    ).also { shares.upsert(it) }

    init {
        fake.signInAs(ana)
    }

    @Test
    fun rapidChangesUploadOnce() = runTest {
        val id = book("Dune")
        pendingRow(id)
        val publisher = publisher()
        repeat(5) {
            publisher.requestSync()
            advanceTimeBy(200.milliseconds)
        }
        assertEquals(0, fake.published.size)
        advanceTimeBy(2001.milliseconds)
        runCurrent()
        assertEquals(1, fake.published.size)
        assertFalse(shares.get(id)!!.pending)
    }

    @Test
    fun publishSendsPublicStateAndBefore() = runTest {
        val id = book("Dune", rating = 4)
        pendingRow(id)
        publisher().syncNow()
        val (state, before) = fake.published.single()
        assertEquals("s$id", state.shareId)
        assertEquals(4, state.rating)
        assertEquals(ReadingStatus.READ, before?.status)
        assertNull(before?.rating)
        val row = shares.get(id)!!
        assertFalse(row.hasBefore)
        assertNull(row.beforeStatus)
    }

    @Test
    fun failedUploadStaysPendingAndRetriesOnNextSync() = runTest {
        val id = book("Dune")
        pendingRow(id)
        val publisher = publisher()
        fake.failPublish = SocialException(SocialException.Kind.OFFLINE, "offline")
        publisher.syncNow()
        assertTrue(shares.get(id)!!.pending)
        assertTrue(shares.get(id)!!.hasBefore)
        fake.failPublish = null
        publisher.syncNow()
        assertEquals(ReadingStatus.READ, fake.published.single().second?.status)
        assertFalse(shares.get(id)!!.pending)
    }

    @Test
    fun stopsAtFirstFailure() = runTest {
        pendingRow(book("A"))
        pendingRow(book("B"))
        fake.failPublish = SocialException(SocialException.Kind.TIMEOUT, "slow")
        publisher().syncNow()
        assertEquals(1, fake.publishAttempts)
    }

    @Test
    fun deletedRowUnpublishedAndRemoved() = runTest {
        val id = book("Dune")
        pendingRow(id, deleted = true)
        books.delete(id)
        publisher().syncNow()
        assertEquals(listOf("s$id"), fake.unpublished)
        assertNull(shares.get(id))
    }

    @Test
    fun excludedRowUnpublishedAndKept() = runTest {
        val id = book("Dune")
        pendingRow(id, excluded = true)
        publisher().syncNow()
        assertEquals(listOf("s$id"), fake.unpublished)
        val row = shares.get(id)!!
        assertTrue(row.excluded)
        assertFalse(row.pending)
    }

    @Test
    fun notAvailableGetsNewShareIdAndContinues() = runTest {
        val taken = book("Taken", rating = 5)
        val other = book("Other")
        pendingRow(taken, shareId = "taken")
        pendingRow(other)
        fake.takenShareIds += "taken"
        val publisher = publisher()
        publisher.syncNow()
        assertEquals(listOf("s$other"), fake.published.map { it.first.shareId })
        val row = shares.get(taken)!!
        assertEquals("new-1", row.shareId)
        assertTrue(row.pending)
        assertEquals(5, row.beforeRating)        // "before" = current state: nothing is posted for it
        publisher.syncNow()
        assertEquals("new-1", fake.published.last().first.shareId)
        assertEquals(5, fake.published.last().second?.rating)
    }

    @Test
    fun orphanRowsAdopted() = runTest {
        val id = book("Dune")
        pendingRow(id, rowOwner = "")
        publisher().syncNow()
        assertEquals(1, fake.published.size)
        assertEquals(ana, shares.get(id)!!.owner)
    }

    @Test
    fun otherOwnersRowsLeftAlone() = runTest {
        val id = book("Dune")
        pendingRow(id, rowOwner = "bruno")
        publisher().syncNow()
        assertTrue(fake.published.isEmpty())
        assertTrue(shares.get(id)!!.pending)
    }

    @Test
    fun nothingWithoutActiveOwner() = runTest {
        pendingRow(book("Dune"))
        owner = null
        publisher().syncNow()
        assertEquals(0, fake.publishAttempts)
    }

    @Test
    fun changedDuringUploadStaysPending() = runTest {
        val id = book("Dune", rating = 3)
        pendingRow(id)
        fake.onPublish = { books.update(books.getById(id)!!.copy(rating = 5)) }
        val publisher = publisher()
        publisher.syncNow()
        val row = shares.get(id)!!
        assertTrue(row.pending)
        assertFalse(row.hasBefore)               // the server has the book now
        fake.onPublish = {}
        publisher.syncNow()
        assertEquals(5, fake.published.last().first.rating)
        assertFalse(shares.get(id)!!.pending)
    }

    @Test
    fun localCoverPhotoSentAsNoCover() = runTest {
        val id = books.insert(Book(title = "Dune", coverUrl = "covers/photo-1.jpg").toEntity())
        pendingRow(id)
        publisher().syncNow()
        assertNull(fake.published.single().first.coverUrl)
        assertNotEquals("", fake.published.single().first.title)
    }

    @Test
    fun rejectedRowIsSkippedAndOthersContinue() = runTest {
        val bad = book("Bad")
        val good = book("Good")
        pendingRow(bad)
        pendingRow(good)
        fake.rejectedShareIds += "s$bad"
        publisher().syncNow()
        assertEquals(listOf("s$good"), fake.published.map { it.first.shareId })
        assertFalse(shares.get(bad)!!.pending)
    }

    @Test
    fun accountSwitchMidRunStops() = runTest {
        pendingRow(book("A"))
        pendingRow(book("B"))
        fake.onPublish = { owner = "bruno" }
        publisher().syncNow()
        assertEquals(1, fake.publishAttempts)
    }

    /** The tracker re-marks a row while the Publisher is checking its upload: the change must not be lost. */
    @Test
    fun changeDuringPostUploadCheckNotLost() = runTest {
        val id = book("Dune", rating = 3)
        pendingRow(id)
        val lock = ShareLock()
        val tracker = ShareTracker(shares, activeOwner = { owner }, onPending = {}, lock = lock)
        var loads = 0
        val publisher = Publisher(
            shares,
            loadBook = { bookId ->
                val current = books.getById(bookId)!!.toBook()
                if (++loads == 2) {
                    backgroundScope.launch {
                        val changed = current.copy(rating = 5)
                        books.update(changed.toEntity())
                        tracker.changed(current, changed)
                    }
                    yield()
                }
                current
            },
            activity = fake.activityService, activeOwner = { owner }, scope = backgroundScope, lock = lock,
        )
        publisher.syncNow()
        runCurrent()
        assertTrue(shares.get(id)!!.pending)
    }
}
