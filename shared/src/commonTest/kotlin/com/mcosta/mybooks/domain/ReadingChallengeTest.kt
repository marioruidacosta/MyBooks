package com.mcosta.mybooks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingChallengeTest {
    private val utc = TimeZone.UTC
    private fun day(text: String) = LocalDate.parse(text).atStartOfDayIn(utc).toEpochMilliseconds()
    private fun read(title: String, date: String?) = Book(title = title, status = ReadingStatus.READ, dateRead = date?.let(::day))

    @Test
    fun booksCountTowardTheYearTheyWereFinished() {
        val books = listOf(
            read("Dec 2025", "2025-12-31"),
            read("Jan 2026", "2026-01-01"),
            read("Oct 2026", "2026-10-24"),
            read("No date", null),
            Book(title = "Reading", status = ReadingStatus.READING, dateRead = day("2026-05-05")),
        )
        assertEquals(listOf("Oct 2026", "Jan 2026"), books.readIn(2026, utc).map { it.title })
        assertEquals(listOf("Dec 2025"), books.readIn(2025, utc).map { it.title })
        assertEquals(listOf("No date"), books.readWithoutDate().map { it.title })
    }

    @Test
    fun paceComparesWithAnEvenPaceThroughTheYear() {
        val nov3 = LocalDate.parse("2026-11-03")                  // day 307 of 365: 30 books → 25 expected
        assertEquals(Pace.Ahead(2), ChallengeProgress(2026, 30, 27).pace(nov3))
        assertEquals(Pace.OnTrack, ChallengeProgress(2026, 30, 25).pace(nov3))
        assertEquals(Pace.Behind(7), ChallengeProgress(2026, 30, 18).pace(nov3))
        assertEquals(Pace.OnTrack, ChallengeProgress(2026, 30, 0).pace(LocalDate.parse("2026-01-01")))
        assertEquals(Pace.Behind(1), ChallengeProgress(2026, 30, 29).pace(LocalDate.parse("2026-12-31")))
    }

    @Test
    fun completePastAndFutureYears() {
        val today = LocalDate.parse("2026-11-03")
        assertEquals(Pace.Complete, ChallengeProgress(2026, 30, 31).pace(today))
        assertEquals(Pace.Complete, ChallengeProgress(2025, 20, 20).pace(today))
        assertEquals(Pace.Ended(3), ChallengeProgress(2025, 25, 22).pace(today))
        assertEquals(Pace.Upcoming, ChallengeProgress(2027, 40, 0).pace(today))
    }

    @Test
    fun percentIsCappedAtHundred() {
        assertEquals(60, ChallengeProgress(2026, 30, 18).percent)
        assertEquals(100, ChallengeProgress(2026, 30, 45).percent)
    }

    @Test
    fun leapYearHas366Days() {
        // 2028-12-31 is day 366: everything expected.
        assertEquals(Pace.Behind(1), ChallengeProgress(2028, 366, 365).pace(LocalDate.parse("2028-12-31")))
        assertEquals(Pace.OnTrack, ChallengeProgress(2028, 366, 60).pace(LocalDate.parse("2028-02-29")))
    }
}
