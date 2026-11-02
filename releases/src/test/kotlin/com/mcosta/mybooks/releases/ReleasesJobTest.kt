package com.mcosta.mybooks.releases

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Serves [records] whose datestamp falls in the requested window, [pageSize] per page. */
class FakeOaiSource(private val records: List<MarcRecord>, private val pageSize: Int = 5) : OaiSource {
    val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
    var failOnCall: Int? = null
    /** Every call from this one on fails (a lasting outage). */
    var failFromCall: Int? = null

    override fun page(from: LocalDate, until: LocalDate, resumptionToken: String?): OaiPage {
        calls += from to until
        if (failOnCall == calls.size || (failFromCall ?: Int.MAX_VALUE) <= calls.size) throw IOException("library down")
        val inWindow = records.filter { LocalDate.parse(it.datestamp) in from..until }
        val start = resumptionToken?.toInt() ?: 0
        val end = minOf(start + pageSize, inWindow.size)
        return OaiPage(inWindow.subList(start, end), if (end < inWindow.size) end.toString() else null)
    }
}

class ReleasesJobTest {
    private val dir: Path = createTempDirectory("releases-test")
    private val store = DataStore(dir)
    private val now = Instant.parse("2026-11-03T03:00:00Z")

    /** The fixture's new books, re-dated. */
    private fun books(vararg dated: Pair<String, String>) = dated.map { (id, date) -> Fixtures.record(id).copy(datestamp = date) }

    private fun job(source: OaiSource, today: String = "2026-11-03") = ReleasesJob(source, store, LocalDate.parse(today), now)

    private fun idsIn(month: String) = store.month(month)!!.books.map { it.id }

    @Test
    fun firstRunBackfillsSixMonthsPlusCurrent() {
        val ids = listOf("2293200", "2290561", "2291160", "2290897", "2291019", "2290983", "2218820")
        val months = listOf("2026-05-10", "2026-06-10", "2026-07-10", "2026-08-10", "2026-09-10", "2026-10-10", "2026-11-02")
        val source = FakeOaiSource(books(*ids.zip(months).toTypedArray()))
        job(source).run()
        assertEquals(LocalDate.parse("2026-05-01"), source.calls.first().first)
        assertEquals(LocalDate.parse("2026-11-02"), source.calls.last().second)
        assertEquals(listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"), store.months())
        assertEquals("2026-11-02", store.state()!!.harvestedUntil)
    }

    @Test
    fun nightlyRunResumesFromHarvestedUntilMinusOne() {
        store.write(JobState("2026-11-01"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        val source = FakeOaiSource(emptyList())
        job(source).run()
        assertEquals(listOf(LocalDate.parse("2026-10-31") to LocalDate.parse("2026-11-02")), source.calls)
    }

    @Test
    fun noDuplicatesAcrossRuns() {
        val records = books("2293200" to "2026-11-01")
        store.write(JobState("2026-10-31"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(records), today = "2026-11-02").run()
        job(FakeOaiSource(records), today = "2026-11-03").run()   // the overlap day serves it again
        assertEquals(listOf("2293200"), idsIn("2026-11"))
    }

    @Test
    fun monthBoundaryAssignsByDatestamp() {
        store.write(JobState("2026-10-30"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(books("2293200" to "2026-10-31")), today = "2026-11-01").run()
        assertEquals(listOf("2293200"), idsIn("2026-10"))
        assertTrue(store.index()!!.months.single { it.month == "2026-10" }.complete)
    }

    @Test
    fun currentMonthIncompletePastComplete() {
        store.write(JobState("2026-10-30"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(books("2293200" to "2026-10-31", "2290561" to "2026-11-02"))).run()
        val index = store.index()!!
        assertEquals(listOf("2026-11", "2026-10"), index.months.map { it.month })
        assertEquals(listOf(false, true), index.months.map { it.complete })
    }

    @Test
    fun indexCountsMatchMonthFiles() {
        store.write(JobState("2026-09-30"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        val ids = listOf("2293200", "2290561", "2291280", "2290698", "2291405", "2290897", "2292020", "2291185")
        job(FakeOaiSource(books(*ids.map { it to "2026-10-15" }.toTypedArray())), today = "2026-11-01").run()
        val october = store.index()!!.months.single { it.month == "2026-10" }
        val file = store.month("2026-10")!!
        assertEquals(file.books.size, october.total)
        assertEquals(file.books.groupingBy { it.genre }.eachCount(), october.genres)
        assertEquals(mapOf("children" to 1, "crime" to 1, "history" to 1, "novel" to 2, "poetry" to 1, "scifi" to 1, "theatre" to 1), october.genres)
        // Samples for the app's tests (shared/src/commonTest/fixtures), so both sides agree on the format.
        val samples = Path.of("build", "samples").createDirectories()
        Files.copy(dir.resolve("releases/index.json"), samples.resolve("index.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.copy(dir.resolve("releases/2026-10.json"), samples.resolve("2026-10.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    @Test
    fun errorWritesNothing() {
        store.write(JobState("2026-10-31"), setOf("1"), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        val before = dir.resolve("state.json").readText() to dir.resolve("seen.txt").readText()
        val source = FakeOaiSource(books(*listOf("2293200", "2290561", "2291160", "2290897", "2291019", "2290983").map { it to "2026-11-01" }.toTypedArray()))
        source.failFromCall = 2
        assertFailsWith<IOException> { ReleasesJob(source, store, LocalDate.parse("2026-11-03"), now, retryDelay = {}).run() }
        assertEquals(before, dir.resolve("state.json").readText() to dir.resolve("seen.txt").readText())
        assertFalse(dir.resolve("releases/2026-11.json").exists())
    }

    @Test
    fun oldMonthsRemoved() {
        val old = listOf("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10")
            .associateWith { MonthFile(month = it, updated = "x", books = emptyList()) }
        store.write(JobState("2026-11-01"), emptySet(), old, IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(books("2293200" to "2026-11-02"))).run()
        assertEquals(listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"), store.months())
        assertEquals(7, store.index()!!.months.size)
    }

    @Test
    fun noRecordsMatchIsEmpty() {
        store.write(JobState("2026-11-01"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(emptyList())).run()
        assertEquals("2026-11-02", store.state()!!.harvestedUntil)
    }

    @Test
    fun seenBooksAreNotListedAgain() {
        store.write(JobState("2026-11-01"), setOf("2293200"), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(books("2293200" to "2026-11-02", "2290561" to "2026-11-02"))).run()
        assertEquals(listOf("2290561"), idsIn("2026-11"))
        assertTrue("2290561" in store.seen())
    }
    @Test
    fun currentMonthListedOnTheFirst() {
        val old = listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10")
            .associateWith { MonthFile(month = it, updated = "x", books = emptyList()) }
        store.write(JobState("2026-11-30"), emptySet(), old + ("2026-11" to MonthFile(month = "2026-11", updated = "x", books = emptyList())),
            IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(emptyList()), today = "2026-12-01").run()
        val index = store.index()!!
        assertEquals("2026-12", index.months.first().month)
        assertFalse(index.months.first().complete)
        assertEquals(7, index.months.size)
        assertEquals(listOf("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11", "2026-12"), store.months())
    }

    @Test
    fun transientFailureIsRetried() {
        store.write(JobState("2026-11-01"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        val source = FakeOaiSource(books("2293200" to "2026-11-02"))
        source.failOnCall = 1
        ReleasesJob(source, store, LocalDate.parse("2026-11-03"), now, retryDelay = {}).run()
        assertEquals(listOf("2293200"), idsIn("2026-11"))
    }

    @Test
    fun repeatedTokenFails() {
        store.write(JobState("2026-11-01"), emptySet(), emptyMap(), IndexFile(updated = "x", months = emptyList()), emptyList())
        val stuck = object : OaiSource {
            override fun page(from: LocalDate, until: LocalDate, resumptionToken: String?) = OaiPage(emptyList(), "same")
        }
        assertFailsWith<OaiException> { ReleasesJob(stuck, store, LocalDate.parse("2026-11-03"), now, retryDelay = {}).run() }
        assertEquals("2026-11-01", store.state()!!.harvestedUntil)
    }
    /** A local run killed after writing a month file but before seen.txt must not list its books twice. */
    @Test
    fun bookInMonthFileButNotSeenIsNotDuplicated() {
        val casa = BookExtractor.extract(Fixtures.record("2293200"))!!
        store.write(JobState("2026-11-01"), emptySet(), mapOf("2026-11" to MonthFile(month = "2026-11", updated = "x", books = listOf(casa))),
            IndexFile(updated = "x", months = emptyList()), emptyList())
        job(FakeOaiSource(books("2293200" to "2026-11-02"))).run()
        assertEquals(listOf("2293200"), idsIn("2026-11"))
    }

}

class DataStoreTest {
    @Test
    fun roundTrip() {
        val dir = createTempDirectory("store-test")
        val store = DataStore(dir)
        assertEquals(null, store.state())
        assertTrue(store.seen().isEmpty())
        val book = ReleaseBook("1", "9789895682799", "T", listOf("A"), emptyList(), "P", "Lisboa", 2026, "novel", "http://x/1")
        val month = MonthFile(month = "2026-10", updated = "2026-11-01T03:00:00Z", books = listOf(book))
        val index = IndexFile(updated = "2026-11-01T03:00:00Z", months = listOf(IndexMonth("2026-10", true, 1, mapOf("novel" to 1))))
        store.write(JobState("2026-10-31"), setOf("1", "2"), mapOf("2026-10" to month), index, emptyList())
        assertEquals(JobState("2026-10-31"), store.state())
        assertEquals(setOf("1", "2"), store.seen())
        assertEquals(month, store.month("2026-10"))
        assertEquals(index, store.index())
        assertEquals(listOf("2026-10"), store.months())
        dir.resolve("releases/notes.txt").writeText("ignored")
        store.write(JobState("2026-11-01"), setOf("1"), emptyMap(), index, remove = listOf("2026-10"))
        assertEquals(emptyList(), store.months())
    }

}
