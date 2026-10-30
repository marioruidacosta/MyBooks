package com.mcosta.mybooks.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ReadingStatus { READ, TO_READ, WISHLIST }

data class Book(
    val id: Long = 0,
    val isbn: String? = null,
    val title: String,
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
)
