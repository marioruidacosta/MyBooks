package com.mcosta.mybooks.data.releases

import com.mcosta.mybooks.domain.searchKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A value and when it was downloaded; [offline] means the download failed and this is the saved copy. */
data class Cached<T>(val value: T, val savedAt: Instant, val offline: Boolean)

/**
 * New in Portugal's lists, saved on the phone. The index and the month in progress are re-downloaded when
 * older than 6 hours, past months after 24 hours; without a network the saved copy is used.
 */
class ReleasesRepository(
    private val client: ReleasesClient,
    private val fs: FileSystem,
    private val dir: Path,
    private val clock: () -> Instant = { Clock.System.now() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The months, newest first. Saved months no longer listed are deleted from the phone. */
    suspend fun months(): Cached<List<ReleaseMonth>> {
        val cached = load("index", CURRENT_MAX_AGE, { client.index() }) { text ->
            json.decodeFromString<IndexJson>(text).months.sortedByDescending { it.month }.map { it.toMonth() }
        }
        if (!cached.offline) withContext(io) { prune(keep = cached.value.map { it.month }.toSet()) }
        return cached
    }

    /**
     * The month's books, A–Z by title (ignoring case and accents). A saved copy whose size no longer matches
     * the saved index (books added after it was downloaded) is downloaded again.
     */
    suspend fun books(month: String): Cached<List<ReleaseBook>> {
        val info = savedIndexMonth(month)
        val maxAge = if (info?.complete == true) PAST_MAX_AGE else CURRENT_MAX_AGE
        return load(month, maxAge, { client.month(month) }, outdated = { books -> info != null && books.size != info.total }) { text ->
            // Keys computed once per book, not per comparison.
            json.decodeFromString<MonthJson>(text).books.map { searchKey(it.title) to it }.sortedBy { it.first }.map { it.second }
        }
    }

    /** The month as listed in the saved index (no download), or null. */
    suspend fun savedMonth(month: String): ReleaseMonth? = savedIndexMonth(month)?.toMonth()

    /** A book from the saved month file (no download). */
    suspend fun book(month: String, id: String): ReleaseBook? = withContext(io) {
        read(month)?.let { (text, _) -> parseOrNull { json.decodeFromString<MonthJson>(text).books.firstOrNull { it.id == id } } }
    }

    private suspend fun savedIndexMonth(month: String): IndexMonthJson? = withContext(io) {
        read("index")?.let { (text, _) -> parseOrNull { json.decodeFromString<IndexJson>(text).months.firstOrNull { it.month == month } } }
    }

    /** Reading, parsing and saving all run on [io], off the main thread. */
    private suspend fun <T : Any> load(
        name: String,
        maxAge: Duration,
        download: suspend () -> String,
        outdated: (T) -> Boolean = { false },
        parse: (String) -> T,
    ): Cached<T> {
        val now = clock()
        val savedValue = withContext(io) {
            read(name)?.let { (text, at) -> parseOrNull { parse(text) }?.let { Cached(it, at, offline = false) } }
        }
        if (savedValue != null && now - savedValue.savedAt < maxAge && !outdated(savedValue.value)) return savedValue
        val text = try {
            download()
        } catch (e: ReleasesUnavailable) {
            if (savedValue != null) return savedValue.copy(offline = true)
            throw e
        }
        // Not our JSON (e.g. a Wi-Fi login page answering instead): treat it like being offline.
        val value = withContext(io) { parseOrNull { parse(text) } }
            ?: return savedValue?.copy(offline = true) ?: throw ReleasesUnavailable(notPublished = false)
        withContext(io) { write(name, text, now) }
        return Cached(value, now, offline = false)
    }

    private fun <T> parseOrNull(block: () -> T): T? = try {
        block()
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** The saved copy and when it was downloaded; null when there is none or it can't be read. */
    private fun read(name: String): Pair<String, Instant>? = try {
        val file = dir / "$name.json"
        val stamp = dir / "$name.at"
        if (!fs.exists(file) || !fs.exists(stamp)) {
            null
        } else {
            fs.read(stamp) { readUtf8() }.trim().toLongOrNull()?.let { at -> fs.read(file) { readUtf8() } to Instant.fromEpochMilliseconds(at) }
        }
    } catch (e: IOException) {
        null
    }

    /** Saving is best effort: a full disk only means the list is downloaded again next time. */
    private fun write(name: String, text: String, at: Instant) {
        try {
            fs.createDirectories(dir)
            fs.write(dir / "$name.json") { writeUtf8(text) }
            fs.write(dir / "$name.at") { writeUtf8(at.toEpochMilliseconds().toString()) }
        } catch (e: IOException) {
            Unit
        }
    }

    /** Deletes saved month files (and their dates) not in [keep]; best effort. */
    private fun prune(keep: Set<String>) {
        try {
            if (!fs.exists(dir)) return
            for (path in fs.list(dir)) {
                val month = path.name.substringBeforeLast('.')
                if (MONTH.matches(month) && month !in keep) fs.delete(path)
            }
        } catch (e: IOException) {
            Unit
        }
    }

    private companion object {
        val MONTH = Regex("""\d{4}-\d{2}""")
        val CURRENT_MAX_AGE = 6.hours
        val PAST_MAX_AGE = 24.hours
    }
}
