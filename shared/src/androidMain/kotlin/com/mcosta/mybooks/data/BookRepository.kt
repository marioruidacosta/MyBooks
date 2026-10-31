package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.backup.BackupFormatException
import com.mcosta.mybooks.data.backup.BackupSerializer
import com.mcosta.mybooks.data.backup.Zip
import com.mcosta.mybooks.data.covers.CoverStore
import com.mcosta.mybooks.data.db.BookDao
import com.mcosta.mybooks.data.db.searchPattern
import com.mcosta.mybooks.data.db.toBook
import com.mcosta.mybooks.data.db.toEntity
import com.mcosta.mybooks.data.lookup.BookLookupService
import com.mcosta.mybooks.data.lookup.LookupResult
import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.Isbn
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data object DuplicateIsbn : SaveResult
    data object InvalidTitle : SaveResult
    data object InvalidRating : SaveResult
    data object InvalidIsbn : SaveResult
}

data class ImportSummary(val added: Int, val updated: Int, val failed: Int)

/** Single entry point for the UI to read and change the library. */
class BookRepository(
    private val dao: BookDao,
    private val lookupService: BookLookupService,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Runs a block atomically; the app passes a Room write transaction. */
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { it() },
    /** Cover photos taken in the app; null in tests that don't involve them. */
    private val coverStore: CoverStore? = null,
) {
    fun observeBooks(status: ReadingStatus?, query: String): Flow<List<Book>> =
        dao.observe(status, searchPattern(query)).map { list -> list.map { it.toBook() } }

    fun observeBook(id: Long): Flow<Book?> = dao.observeById(id).map { it?.toBook() }

    suspend fun getBook(id: Long): Book? = dao.getById(id)?.toBook()

    suspend fun bookCount(): Int = dao.getAll().size

    suspend fun findByIsbn(isbn: String): Book? =
        Isbn.normalize(isbn)?.let { dao.findByIsbn(it)?.toBook() }

    suspend fun lookup(isbn: String): LookupResult = lookupService.lookup(isbn)

    suspend fun save(book: Book): SaveResult {
        val validated = when (val v = validate(book)) {
            is Validation.Ok -> v.book
            is Validation.Invalid -> return v.result
        }
        if (validated.isbn != null) {
            val existing = dao.findByIsbn(validated.isbn)
            if (existing != null && existing.id != validated.id) return SaveResult.DuplicateIsbn
        }
        return if (validated.id == 0L) {
            SaveResult.Saved(dao.insert(withStatusDates(validated.copy(dateAdded = clock())).toEntity()))
        } else {
            val stored = dao.getById(validated.id)
            val merged = validated.copy(dateAdded = stored?.dateAdded ?: validated.dateAdded)
            dao.update(withStatusDates(merged).toEntity())
            if (stored != null && stored.coverUrl != merged.coverUrl) coverStore?.delete(stored.coverUrl)
            SaveResult.Saved(validated.id)
        }
    }

    suspend fun setStatus(id: Long, status: ReadingStatus) =
        modify(id) { withStatusDates(it.copy(status = status)) }

    suspend fun setRating(id: Long, rating: Int?) {
        require(rating == null || rating in 1..5) { "rating must be 1..5 or null" }
        modify(id) { it.copy(rating = rating) }
    }

    suspend fun setNotes(id: Long, notes: String) = modify(id) { it.copy(notes = notes) }

    /** Progress by page; the percentage follows from the page count when known (capped at 100). */
    suspend fun setProgressPages(id: Long, page: Int) {
        require(page >= 0) { "page must be >= 0" }
        modify(id) { book ->
            val percent = book.pageCount?.takeIf { it > 0 }?.let { minOf(100, page * 100 / it) }
            book.copy(currentPage = page, progressPercent = percent)
        }
    }

    /** Progress by percentage, for books without a page count or read as ebooks. */
    suspend fun setProgressPercent(id: Long, percent: Int) {
        require(percent in 0..100) { "percent must be 0..100" }
        modify(id) { it.copy(currentPage = null, progressPercent = percent) }
    }

    suspend fun setDateStarted(id: Long, date: Long) = modify(id) { it.copy(dateStarted = date) }

    /** Sets the cover (e.g. a new photo) and removes the previous cover photo, if any. */
    suspend fun setCover(id: Long, coverUrl: String?) {
        val old = dao.getById(id)?.coverUrl
        modify(id) { it.copy(coverUrl = coverUrl) }
        if (old != coverUrl) coverStore?.delete(old)
    }

    suspend fun delete(id: Long) {
        val cover = dao.getById(id)?.coverUrl
        dao.delete(id)
        coverStore?.delete(cover)
    }

    /** A .zip backup: library.json plus the cover photos taken in the app (covers/<name>). */
    suspend fun exportBackup(): ByteArray {
        val books = dao.getAll().map { it.toBook() }
        val photos = books.mapNotNull { it.coverUrl }.distinct().mapNotNull { ref ->
            val name = coverStore?.nameOf(ref) ?: return@mapNotNull null
            val bytes = coverStore.read(ref) ?: return@mapNotNull null
            (BACKUP_COVERS + name) to bytes
        }
        return Zip.write(listOf(BACKUP_LIBRARY to BackupSerializer.encode(books, clock()).encodeToByteArray()) + photos)
    }

    /** Restores a .zip backup (with cover photos) or an older plain .json backup. */
    suspend fun importBackup(bytes: ByteArray): ImportSummary {
        if (!Zip.isZip(bytes)) return importJson(bytes.decodeToString())
        val entries = Zip.read(bytes)
        val library = entries.firstOrNull { it.first == BACKUP_LIBRARY }?.second?.decodeToString()
            ?: throw BackupFormatException("Not a MyBooks backup file")
        BackupSerializer.decode(library) // validate before writing any photo
        for ((name, data) in entries) {
            val photo = name.removePrefix(BACKUP_COVERS)
            if (name.startsWith(BACKUP_COVERS) && CoverStore.isSafeName(photo)) coverStore?.put(photo, data)
        }
        return importJson(library)
    }

    suspend fun exportJson(): String = BackupSerializer.encode(dao.getAll().map { it.toBook() }, clock())

    /**
     * Merges a backup into the library: books whose ISBN is already present are updated, the rest
     * inserted. Invalid entries (and repeats of an ISBN within the file) are counted as failed.
     * Throws [BackupFormatException] before writing anything if [json] is not a valid backup.
     */
    suspend fun importJson(json: String): ImportSummary {
        val books = BackupSerializer.decode(json)
        var added = 0
        var updated = 0
        var failed = 0
        transactionRunner {
            val seenIsbns = mutableSetOf<String>()
            for (raw in books) {
                val book = (validate(raw.copy(id = 0)) as? Validation.Ok)?.book
                if (book == null || (book.isbn != null && !seenIsbns.add(book.isbn))) {
                    failed++
                    continue
                }
                // Books without ISBN match on title + authors, so restoring the same backup twice doesn't duplicate them.
                val existing = if (book.isbn != null) {
                    dao.findByIsbn(book.isbn)
                } else {
                    dao.findWithoutIsbn(book.title, book.authors.trim())
                }
                if (existing != null) {
                    dao.update(withStatusDates(book.copy(id = existing.id, dateAdded = existing.dateAdded)).toEntity())
                    updated++
                } else {
                    val dateAdded = if (book.dateAdded > 0) book.dateAdded else clock()
                    dao.insert(withStatusDates(book.copy(dateAdded = dateAdded)).toEntity())
                    added++
                }
            }
        }
        return ImportSummary(added, updated, failed)
    }

    private suspend fun modify(id: Long, change: (Book) -> Book) {
        val book = dao.getById(id)?.toBook() ?: return
        dao.update(change(book).toEntity())
    }

    /**
     * Status rules: Reading records a start date; Read records a read date and keeps the start date and
     * progress as history; To read / To buy mean "not started", so start date and progress are cleared.
     */
    private fun withStatusDates(book: Book): Book = when (book.status) {
        ReadingStatus.READING -> book.copy(dateRead = null, dateStarted = book.dateStarted ?: clock())
        ReadingStatus.READ -> book.copy(dateRead = book.dateRead ?: clock())
        ReadingStatus.TO_READ, ReadingStatus.WISHLIST ->
            book.copy(dateRead = null, dateStarted = null, currentPage = null, progressPercent = null)
    }

    private companion object {
        const val BACKUP_LIBRARY = "library.json"
        const val BACKUP_COVERS = "covers/"
    }

    internal sealed interface Validation {
        data class Ok(val book: Book) : Validation
        data class Invalid(val result: SaveResult) : Validation
    }

    /** Applies the library rules shared by [save] and backup import. */
    internal fun validate(book: Book): Validation {
        val title = book.title.trim()
        if (title.isEmpty()) return Validation.Invalid(SaveResult.InvalidTitle)
        if (book.rating != null && book.rating !in 1..5) return Validation.Invalid(SaveResult.InvalidRating)
        val isbn = book.isbn?.takeIf { it.isNotBlank() }?.let {
            Isbn.normalize(it) ?: return Validation.Invalid(SaveResult.InvalidIsbn)
        }
        return Validation.Ok(
            book.copy(
                title = title,
                isbn = isbn,
                currentPage = book.currentPage?.takeIf { it >= 0 },
                progressPercent = book.progressPercent?.coerceIn(0, 100),
            )
        )
    }
}
