package com.mcosta.mybooks.releases

import kotlinx.serialization.Serializable

/** A UNIMARC data field: its tag and subfields in order (a code may repeat). */
data class DataField(val tag: String, val subfields: List<Pair<Char, String>>) {
    fun all(code: Char): List<String> = subfields.filter { it.first == code }.map { it.second }
    fun first(code: Char): String? = all(code).firstOrNull()
}

/** One record of the National Bibliography, as harvested. [datestamp] is the OAI datestamp (`YYYY-MM-DD`). */
data class MarcRecord(
    val id: String,
    val datestamp: String,
    val leader: String,
    val controls: Map<String, String>,
    val fields: List<DataField>,
) {
    fun fields(tag: String): List<DataField> = fields.filter { it.tag == tag }
}

/** A new book as published in the month files (field names are the published JSON format). */
@Serializable
data class ReleaseBook(
    val id: String,
    val isbn: String,
    val title: String,
    val authors: List<String>,
    val translators: List<String>,
    val publisher: String? = null,
    val place: String? = null,
    val year: Int,
    val genre: String,
    val link: String? = null,
)
