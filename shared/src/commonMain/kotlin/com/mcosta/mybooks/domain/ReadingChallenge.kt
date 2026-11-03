package com.mcosta.mybooks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The most books a yearly goal can ask for. */
const val MAX_GOAL = 999

/** A year's reading challenge: read [goal] books in [year]. */
data class Challenge(val year: Int, val goal: Int)

/** The year a Read book was finished, or null when it isn't Read or has no read date. */
fun Book.readYear(timeZone: TimeZone): Int? =
    dateRead?.takeIf { status == ReadingStatus.READ }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).year }

/** The books finished in [year], most recent first: they count toward that year's challenge. */
fun List<Book>.readIn(year: Int, timeZone: TimeZone): List<Book> =
    filter { it.readYear(timeZone) == year }.sortedByDescending { it.dateRead }

/** Read books without a read date: they count toward no year. */
fun List<Book>.readWithoutDate(): List<Book> = filter { it.status == ReadingStatus.READ && it.dateRead == null }

/** A challenge and the books read toward it so far. */
data class ChallengeProgress(val year: Int, val goal: Int, val read: Int) {
    val complete: Boolean get() = read >= goal
    val percent: Int get() = if (goal <= 0) 100 else minOf(100, read * 100 / goal)
}

/** Where a challenge stands on [today]. */
sealed interface Pace {
    data object Complete : Pace
    /** A future year. */
    data object Upcoming : Pace
    /** A past year that wasn't completed, [short] books short. */
    data class Ended(val short: Int) : Pace
    data object OnTrack : Pace
    data class Ahead(val books: Int) : Pace
    data class Behind(val books: Int) : Pace
}

/**
 * Compared with reading at an even pace through the year: 30 books by 3 November (day 307 of 365) means 25 by
 * now (rounded down), so 27 read is 2 ahead.
 */
fun ChallengeProgress.pace(today: LocalDate): Pace = when {
    complete -> Pace.Complete
    year > today.year -> Pace.Upcoming
    year < today.year -> Pace.Ended(goal - read)
    else -> {
        val expected = goal * today.dayOfYear / daysIn(year)
        val difference = read - expected
        when {
            difference > 0 -> Pace.Ahead(difference)
            difference < 0 -> Pace.Behind(-difference)
            else -> Pace.OnTrack
        }
    }
}

private fun daysIn(year: Int): Int = if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 366 else 365
