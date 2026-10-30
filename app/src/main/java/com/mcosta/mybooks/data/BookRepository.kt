package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.db.BookDao
import com.mcosta.mybooks.data.db.escapeLike
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

/** Single entry point for the UI to read and change the library. */
class BookRepository(
    private val dao: BookDao,
    private val lookupService: BookLookupService,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeBooks(status: ReadingStatus?, query: String): Flow<List<Book>> =
        dao.observe(status, escapeLike(query.trim())).map { list -> list.map { it.toBook() } }

    fun observeBook(id: Long): Flow<Book?> = dao.observeById(id).map { it?.toBook() }

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
