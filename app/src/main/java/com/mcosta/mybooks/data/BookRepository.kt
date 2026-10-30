package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.backup.BackupFormatException
import com.mcosta.mybooks.data.backup.BackupSerializer
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
    /** Runs a block atomically; the app passes Room's `withTransaction`. */
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { it() },
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
            SaveResult.Saved(dao.insert(withReadDate(validated.copy(dateAdded = clock())).toEntity()))
        } else {
            val stored = dao.getById(validated.id)
            val merged = validated.copy(dateAdded = stored?.dateAdded ?: validated.dateAdded)
            dao.update(withReadDate(merged).toEntity())
            SaveResult.Saved(validated.id)
        }
    }

    suspend fun setStatus(id: Long, status: ReadingStatus) =
        modify(id) { withReadDate(it.copy(status = status)) }

    suspend fun setRating(id: Long, rating: Int?) {
        require(rating == null || rating in 1..5) { "rating must be 1..5 or null" }
        modify(id) { it.copy(rating = rating) }
    }

    suspend fun setNotes(id: Long, notes: String) = modify(id) { it.copy(notes = notes) }

    suspend fun delete(id: Long) = dao.delete(id)

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
                    dao.update(withReadDate(book.copy(id = existing.id, dateAdded = existing.dateAdded)).toEntity())
                    updated++
                } else {
                    val dateAdded = if (book.dateAdded > 0) book.dateAdded else clock()
                    dao.insert(withReadDate(book.copy(dateAdded = dateAdded)).toEntity())
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

    private fun withReadDate(book: Book): Book = when {
        book.status != ReadingStatus.READ -> book.copy(dateRead = null)
        book.dateRead == null -> book.copy(dateRead = clock())
        else -> book
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
        return Validation.Ok(book.copy(title = title, isbn = isbn))
    }
}
