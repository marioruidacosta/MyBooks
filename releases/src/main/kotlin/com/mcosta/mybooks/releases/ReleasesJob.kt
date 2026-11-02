package com.mcosta.mybooks.releases

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** How many months are kept: the current month and the six before it. */
const val MONTHS_KEPT = 7

/**
 * One run of the job: harvests the National Bibliography records changed since the last run (or, the first
 * time, since the first day of the month six months back), lists the new books in the month they first
 * appeared, and writes everything at the end, so a failed run leaves the data untouched.
 */
class ReleasesJob(
    private val source: OaiSource,
    private val store: DataStore,
    private val today: LocalDate,
    private val now: Instant,
) {
    data class Summary(val windows: Int, val pages: Int, val newBooks: Int)

    fun run(): Summary {
        val yesterday = today.minusDays(1)
        val state = store.state()
        val windows = if (state == null) backfillWindows(yesterday) else listOf(LocalDate.parse(state.harvestedUntil).minusDays(1) to yesterday)

        val seen = store.seen().toMutableSet()
        val changed = mutableMapOf<String, MutableList<ReleaseBook>>()
        var pages = 0
        var added = 0
        for ((from, until) in windows) {
            if (from > until) continue
            var token: String? = null
            do {
                val page = source.page(from, until, token)
                pages++
                for (record in page.records) {
                    val book = BookExtractor.extract(record) ?: continue
                    if (!seen.add(book.id)) continue
                    val month = record.datestamp.take(7)
                    changed.getOrPut(month) { store.month(month)?.books?.toMutableList() ?: mutableListOf() } += book
                    added++
                }
                token = page.resumptionToken
            } while (token != null)
        }

        val stamp = now.toString()
        val newFiles = changed.mapValues { (month, books) -> MonthFile(month = month, updated = stamp, books = books) }
        val allMonths = (store.months() + newFiles.keys).distinct().sortedDescending()
        val kept = allMonths.take(MONTHS_KEPT)
        val current = YearMonth.from(today).toString()
        val index = IndexFile(
            updated = stamp,
            months = kept.map { month ->
                val books = (newFiles[month] ?: store.month(month))?.books.orEmpty()
                IndexMonth(
                    month = month,
                    complete = month < current,
                    total = books.size,
                    genres = books.groupingBy { it.genre }.eachCount().toSortedMap(),
                )
            },
        )
        store.write(
            state = JobState(maxOf(yesterday, LocalDate.parse(state?.harvestedUntil ?: yesterday.toString())).toString()),
            seen = seen,
            months = newFiles.filterKeys { it in kept },
            index = index,
            remove = allMonths.drop(MONTHS_KEPT),
        )
        return Summary(windows.size, pages, added)
    }

    /** One window per calendar month, from the first day of the month six months back up to [lastDay]. */
    private fun backfillWindows(lastDay: LocalDate): List<Pair<LocalDate, LocalDate>> {
        var month = YearMonth.from(today).minusMonths((MONTHS_KEPT - 1).toLong())
        val windows = mutableListOf<Pair<LocalDate, LocalDate>>()
        while (month.atDay(1) <= lastDay) {
            windows += month.atDay(1) to minOf(month.atEndOfMonth(), lastDay)
            month = month.plusMonths(1)
        }
        return windows
    }
}
