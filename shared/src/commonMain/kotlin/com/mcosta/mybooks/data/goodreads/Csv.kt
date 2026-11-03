package com.mcosta.mybooks.data.goodreads

/** Comma-separated values (RFC 4180): quoted fields may hold commas, doubled quotes and line breaks. */
object Csv {
    /** The rows of [text]; a leading byte-order mark and blank lines are ignored, CRLF and LF both end a row. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = if (text.startsWith('﻿')) 1 else 0
        fun endRow() {
            row += field.toString()
            field.clear()
            if (row.size > 1 || row[0].isNotEmpty()) rows += row
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted && c == '\r' && text.getOrNull(i + 1) == '\n' -> {}     // CRLF inside a field is one line break
                quoted -> field.append(c)
                c == '"' -> quoted = true
                c == ',' -> { row += field.toString(); field.clear() }
                c == '\r' && text.getOrNull(i + 1) == '\n' -> {}
                c == '\n' || c == '\r' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** [rows] as CSV with CRLF line ends; fields with commas, quotes or line breaks are quoted. */
    fun write(rows: List<List<String>>): String = buildString {
        for (row in rows) {
            append(row.joinToString(",") { f -> if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${f.replace("\"", "\"\"")}\"" else f })
            append("\r\n")
        }
    }
}
