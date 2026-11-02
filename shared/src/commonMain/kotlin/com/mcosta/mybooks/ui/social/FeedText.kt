package com.mcosta.mybooks.ui.social

import com.mcosta.mybooks.social.model.ActivityKind
import com.mcosta.mybooks.social.model.FeedEntry
import com.mcosta.mybooks.ui.formatDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What the person did, after their name: "started reading", "rated ★★★★☆", "is 60% through". */
fun actionText(entry: FeedEntry): String = when (entry.kind) {
    ActivityKind.STARTED -> "started reading"
    ActivityKind.FINISHED -> "finished"
    ActivityKind.WANT_TO_READ -> "wants to read"
    ActivityKind.WANT_TO_BUY -> "wants to buy"
    ActivityKind.REVIEWED -> "reviewed"
    ActivityKind.RATED -> entry.rating?.coerceIn(0, 5)?.let { "rated " + "★".repeat(it) + "☆".repeat(5 - it) } ?: "rated"
    ActivityKind.PROGRESS -> entry.progress?.let { "is $it% through" } ?: "is reading"
}

/** "just now", "5 min ago", "2 h ago", "3 d ago", then the date. */
fun relativeTime(then: Instant, now: Instant = Clock.System.now()): String {
    val elapsed = now - then
    return when {
        elapsed < 60.seconds -> "just now"
        elapsed < 60.minutes -> "${elapsed.inWholeMinutes} min ago"
        elapsed < 24.hours -> "${elapsed.inWholeHours} h ago"
        elapsed < 7.days -> "${elapsed.inWholeDays} d ago"
        else -> formatDate(then.toEpochMilliseconds())
    }
}
