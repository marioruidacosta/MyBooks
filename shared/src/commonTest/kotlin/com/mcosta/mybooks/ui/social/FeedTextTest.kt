package com.mcosta.mybooks.ui.social

import com.mcosta.mybooks.social.model.ActivityKind
import com.mcosta.mybooks.social.model.FeedBook
import com.mcosta.mybooks.social.model.FeedEntry
import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.ui.formatDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FeedTextTest {
    private fun entry(kind: ActivityKind, rating: Int? = null, progress: Int? = null) = FeedEntry(
        id = 1, actor = Person("id", "ana", "Ana", null), kind = kind, rating = rating, progress = progress,
        createdAt = Instant.fromEpochSeconds(0), book = FeedBook("s", null, "Dune", "Frank Herbert", null),
        review = null, likeCount = 0, likedByMe = false,
    )

    @Test
    fun actionTextForEachKind() {
        assertEquals("started reading", actionText(entry(ActivityKind.STARTED)))
        assertEquals("finished", actionText(entry(ActivityKind.FINISHED)))
        assertEquals("rated ★★★★☆", actionText(entry(ActivityKind.RATED, rating = 4)))
        assertEquals("wants to read", actionText(entry(ActivityKind.WANT_TO_READ)))
        assertEquals("wants to buy", actionText(entry(ActivityKind.WANT_TO_BUY)))
        assertEquals("reviewed", actionText(entry(ActivityKind.REVIEWED)))
        assertEquals("is 60% through", actionText(entry(ActivityKind.PROGRESS, progress = 60)))
    }

    private val now = Instant.parse("2026-11-02T12:00:00Z")
    private fun ago(d: Duration) = relativeTime(now - d, now)

    @Test
    fun relativeTimeBoundaries() {
        assertEquals("just now", ago(59.seconds))
        assertEquals("1 min ago", ago(61.seconds))
        assertEquals("59 min ago", ago(59.minutes))
        assertEquals("1 h ago", ago(60.minutes))
        assertEquals("23 h ago", ago(23.hours))
        assertEquals("1 d ago", ago(24.hours))
        assertEquals("6 d ago", ago(6.days))
        assertEquals(formatDate((now - 7.days).toEpochMilliseconds()), ago(7.days))
        assertEquals(formatDate((now - 8.days).toEpochMilliseconds()), ago(8.days))
    }

    @Test
    fun futureTimesAreJustNow() {
        assertEquals("just now", relativeTime(now + 30.seconds, now))
    }
}
