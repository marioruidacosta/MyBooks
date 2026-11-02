package com.mcosta.mybooks.data.backup

import com.mcosta.mybooks.domain.Book
import com.mcosta.mybooks.domain.ReadingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BackupSerializerTest {
    private val full = Book(
        id = 9, isbn = "9780441013593", title = "Dune", authors = "Frank Herbert", publisher = "Ace",
        publishedYear = 1990, pageCount = 535, coverUrl = "https://x/y.jpg", description = "desc",
        status = ReadingStatus.READ, rating = 5, notes = "great", dateAdded = 100, dateRead = 200,
        dateStarted = 50, currentPage = 535, progressPercent = 100,
    )

    @Test
    fun roundTripPreservesFieldsExceptId() {
        val decoded = BackupSerializer.decode(BackupSerializer.encode(listOf(full), exportedAt = 1))
        assertEquals(listOf(full.copy(id = 0)), decoded)
    }

    @Test
    fun encodeHasVersionAndOmitsId() {
        val json = BackupSerializer.encode(listOf(full), exportedAt = 1761811200000)
        assertEquals(true, json.contains("\"version\":1"))
        assertEquals(true, json.contains("\"exportedAt\":1761811200000"))
        assertFalse(json.contains("\"id\""))
    }

    @Test
    fun unknownFieldsIgnoredAndDefaultsApplied() {
        val json = """{"version":1,"exportedAt":0,"extra":true,"books":[{"title":"Emma","status":"WISHLIST","shelf":"A"}]}"""
        assertEquals(listOf(Book(title = "Emma", status = ReadingStatus.WISHLIST)), BackupSerializer.decode(json))
    }

    @Test
    fun version2Rejected() {
        assertFailsWith<BackupFormatException> { BackupSerializer.decode("""{"version":2,"exportedAt":0,"books":[]}""") }
    }

    @Test
    fun corruptJsonRejected() {
        assertFailsWith<BackupFormatException> { BackupSerializer.decode("not json at all") }
    }

    @Test
    fun backupRoundTripKeepsReviewAndShareInfo() {
        val reviewed = full.copy(review = "A classic")
        val other = full.copy(id = 10, isbn = null, title = "Emma")
        val json = BackupSerializer.encode(listOf(reviewed, other), exportedAt = 1, shares = mapOf(9L to BackupShare("share-9", excluded = true)))
        val decoded = BackupSerializer.decodeWithShares(json)
        assertEquals(listOf(reviewed.copy(id = 0) to BackupShare("share-9", true), other.copy(id = 0) to null), decoded)
    }

    @Test
    fun oldBackupWithoutNewFieldsStillDecodes() {
        val json = """{"version":1,"exportedAt":0,"books":[{"title":"Emma","status":"READ"}]}"""
        assertEquals(listOf(Book(title = "Emma", status = ReadingStatus.READ) to null), BackupSerializer.decodeWithShares(json))
    }

    @Test
    fun unknownStatusRejected() {
        assertFailsWith<BackupFormatException> { BackupSerializer.decode("""{"version":1,"exportedAt":0,"books":[{"title":"X","status":"LOST"}]}""") }
    }
}
