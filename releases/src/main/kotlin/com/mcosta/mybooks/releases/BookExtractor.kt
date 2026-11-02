package com.mcosta.mybooks.releases

/** Turns a National Bibliography record into a listed book, applying the spec's "new book" rules. */
object BookExtractor {
    private const val AUTHOR = "070"
    private const val TRANSLATOR = "730"
    private val YEAR = Regex("""\d{4}""")
    private val SPACES = Regex("""\s+""")
    /** ISBD separators left at the end of a subfield ("Lisboa :", "Letras Lavadas ;", "título /"). */
    private val TRAILING_SEPARATOR = Regex("""\s*[;:/,=]+$""")
    private val CONTROLS =Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F]")

    /** The book, or null when the record isn't a new printed book with an ISBN (already-listed ids are the caller's job). */
    fun extract(record: MarcRecord): ReleaseBook? {
        if (record.leader.length < 8 || record.leader.substring(6, 8) != "am") return null
        val isbn = record.fields("010").flatMap { it.all('a') }.firstNotNullOfOrNull(::isbn13) ?: return null
        val imprint = record.fields("210").firstOrNull() ?: return null
        val year = imprint.all('d').firstNotNullOfOrNull { YEAR.find(it)?.value?.toInt() } ?: return null
        val monthYear = record.datestamp.take(4).toIntOrNull() ?: return null
        if (year != monthYear && year != monthYear - 1) return null
        val titleField = record.fields("200").firstOrNull() ?: return null
        val title = titleField.first('a')?.let(::tidy)?.ifEmpty { null } ?: return null
        val subtitle = titleField.first('e')?.let(::tidy)?.ifEmpty { null }

        val authors = (record.fields("700") + record.fields("701"))
            .filter { it.first('4').let { role -> role == null || role == AUTHOR } }
            .mapNotNull(::name)
            .ifEmpty { titleField.all('f').map(::tidy).filter(String::isNotEmpty) }
        val translators = (record.fields("700") + record.fields("701") + record.fields("702"))
            .filter { it.first('4') == TRANSLATOR }
            .mapNotNull(::name)

        return ReleaseBook(
            id = record.id,
            isbn = isbn,
            title = if (subtitle != null) "$title: $subtitle" else title,
            authors = authors.distinct(),
            translators = translators.distinct(),
            publisher = imprint.first('c')?.let(::tidy)?.ifEmpty { null },
            place = imprint.first('a')?.let(::tidy)?.ifEmpty { null },
            year = year,
            genre = GenreClassifier.classify(record.fields("675").mapNotNull { it.first('a') }),
            link = record.controls["003"],
            cover = record.fields("856").flatMap { it.all('u') }.firstOrNull { "/ImagesBN/" in it }?.trim()?.replaceFirst("http://", "https://"),
        )
    }

    /** "Forename Surname" from `$b` and `$a`. */
    private fun name(f: DataField): String? =
        listOfNotNull(f.first('b'), f.first('a')).joinToString(" ").let(::tidy).ifEmpty { null }

    /**
     * Removes UNIMARC's non-sorting markers (U+0098 / U+009C, around leading articles such as "A " or "Os ")
     * and other control characters, collapses whitespace, and drops a single closing full stop (ISBD
     * punctuation), keeping "...".
     */
    private fun tidy(s: String): String {
        val t = s.replace(CONTROLS, "").replace(SPACES, " ").trim().replace(TRAILING_SEPARATOR, "")
        return if (t.endsWith(".") && !t.endsWith("..")) t.dropLast(1).trimEnd() else t
    }
}
