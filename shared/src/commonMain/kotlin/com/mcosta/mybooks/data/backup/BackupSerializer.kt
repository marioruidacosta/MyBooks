package com.mcosta.mybooks.data.backup

import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Library backup file format: `{ "version": 1, "exportedAt": ..., "books": [...] }`. */
object BackupSerializer {
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(books: List<Book>, exportedAt: Long): String =
        json.encodeToString(BackupFile(VERSION, exportedAt, books.map { it.toBackup() }))

    /** Throws [BackupFormatException] if [text] is not a valid version-1 backup. */
    fun decode(text: String): List<Book> {
        val file = try {
            json.decodeFromString<BackupFile>(text)
        } catch (e: SerializationException) {
            throw BackupFormatException("Not a MyBooks backup file", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Not a MyBooks backup file", e)
        }
        if (file.version != VERSION) throw BackupFormatException("Unsupported backup version ${file.version}")
        return file.books.map { it.toBook() }
    }

    @Serializable
    private data class BackupFile(val version: Int, val exportedAt: Long = 0, val books: List<BackupBook> = emptyList())

    @Serializable
    private data class BackupBook(
        val isbn: String? = null,
        val title: String = "",
        val authors: String = "",
        val publisher: String? = null,
        val publishedYear: Int? = null,
        val pageCount: Int? = null,
        val coverUrl: String? = null,
        val description: String? = null,
        val status: ReadingStatus = ReadingStatus.TO_READ,
        val rating: Int? = null,
        val notes: String = "",
        val dateAdded: Long = 0,
        val dateRead: Long? = null,
        val dateStarted: Long? = null,
        val currentPage: Int? = null,
        val progressPercent: Int? = null,
    ) {
        fun toBook() = Book(
            isbn = isbn, title = title, authors = authors, publisher = publisher, publishedYear = publishedYear,
            pageCount = pageCount, coverUrl = coverUrl, description = description, status = status,
            rating = rating, notes = notes, dateAdded = dateAdded, dateRead = dateRead,
            dateStarted = dateStarted, currentPage = currentPage, progressPercent = progressPercent,
        )
    }

    private fun Book.toBackup() = BackupBook(
        isbn = isbn, title = title, authors = authors, publisher = publisher, publishedYear = publishedYear,
        pageCount = pageCount, coverUrl = coverUrl, description = description, status = status,
        rating = rating, notes = notes, dateAdded = dateAdded, dateRead = dateRead,
        dateStarted = dateStarted, currentPage = currentPage, progressPercent = progressPercent,
    )
}
