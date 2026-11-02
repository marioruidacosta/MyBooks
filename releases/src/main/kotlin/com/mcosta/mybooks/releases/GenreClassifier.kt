package com.mcosta.mybooks.releases

/**
 * Genre key for a record's CDU/UDC codes (`675$a`, in order), by the spec's rules: children's first, then
 * literature by the National Library's genre codes (the `-` form of the first `82…` code), then language,
 * then non-fiction by the first code's main class.
 */
object GenreClassifier {
    private val FORM = Regex("""-(\d+(?:\.\d+)*)""")
    private val CHILDREN_FORM = Regex("""-93(?![0-9])""")

    private val NON_FICTION = mapOf(
        '0' to "general", '1' to "philosophy", '2' to "religion", '3' to "social",
        '5' to "science", '6' to "applied", '7' to "arts", '9' to "history",
    )

    fun classify(udc: List<String>): String {
        val codes = udc.map(String::trim).filter(String::isNotEmpty)
        if (codes.isEmpty()) return "unclassified"
        if (codes.any { it.startsWith("087.5") || (it.startsWith("82") && CHILDREN_FORM.containsMatchIn(it)) }) return "children"
        codes.firstOrNull { it.startsWith("82") }?.let { return literature(FORM.find(it)?.groupValues?.get(1)) }
        if (codes.any { it.startsWith("81") }) return "language"
        return NON_FICTION[codes.first().first()] ?: "other"
    }

    private fun literature(form: String?): String = when {
        form == null -> "literature"
        form.startsWith("312.4") -> "crime"
        form.startsWith("312.9") -> "fantasy"
        form.startsWith("311.9") -> "scifi"
        form.startsWith("311.6") -> "historical"
        form.startsWith("311.3") || form.startsWith("311.8") -> "adventure"
        form.startsWith("31") -> "novel"
        form.startsWith("32") || form.startsWith("34") -> "stories"
        form.startsWith("94") -> "biography"
        form.first() == '1' -> "poetry"
        form.first() == '2' -> "theatre"
        form.first() == '4' -> "essays"
        form.first() == '7' -> "humour"
        else -> "literature"
    }
}
