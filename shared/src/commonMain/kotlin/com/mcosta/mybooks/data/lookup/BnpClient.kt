package com.mcosta.mybooks.data.lookup

import com.mcosta.mybooks.domain.Book
import io.ktor.client.HttpClient
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * Biblioteca Nacional de Portugal (PORBASE) URN resolver, which returns Dublin Core XML for an ISBN.
 * A missing record is HTTP 200 with `<urn-response><error>Registo inexistente</error></urn-response>`.
 */
class BnpClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://urn.porbase.org",
) : BookLookupService {

    override suspend fun lookup(isbn: String): LookupResult = lookupCatching {
        val body = http.getOrNull("$baseUrl/isbn/dc/xml?id=$isbn") ?: return LookupResult.NotFound
        val record = parseDublinCore(body) ?: return LookupResult.NotFound
        fun values(name: String): List<String> = record[name].orEmpty().map(::cleanText).filter { it.isNotEmpty() }
        val title = values("title").firstOrNull()?.let(::bnpTitle)?.takeIf { it.isNotBlank() }
            ?: return LookupResult.NotFound
        LookupResult.Found(
            Book(
                isbn = isbn,
                title = title,
                authors = values("creator").map(::bnpPersonName).joinToString(", "),
                // dc:publisher lists the publisher first, then the place of publication.
                publisher = values("publisher").firstOrNull(),
                publishedYear = yearFrom(values("date").firstOrNull()),
                pageCount = values("format").firstNotNullOfOrNull(::pagesFrom),
                coverUrl = httpsUrl(values("identifier").firstOrNull { "/ImagesBN/" in it }),
            )
        )
    }

    /** Dublin Core element texts by local name, or null when the root isn't `dc` (e.g. `urn-response` errors). */
    private fun parseDublinCore(text: String): Map<String, List<String>>? {
        // The generic (pure Kotlin) reader behaves the same on every platform.
        val reader = xmlStreaming.newGenericReader(text)
        val values = mutableMapOf<String, MutableList<String>>()
        var root: String? = null
        while (reader.hasNext()) {
            if (reader.next() != EventType.START_ELEMENT) continue
            if (root == null) root = reader.localName
            if (reader.namespaceURI == DC_NS) values.getOrPut(reader.localName) { mutableListOf() }.add(reader.elementText())
        }
        return if (root == "dc") values else null
    }

    private fun XmlReader.elementText(): String {
        val text = StringBuilder()
        while (true) {
            when (next()) {
                EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE -> text.append(this.text)
                EventType.END_ELEMENT -> return text.toString()
                else -> Unit
            }
        }
    }

    private companion object {
        const val DC_NS = "http://purl.org/dc/elements/1.1/"
    }
}

/** Removes the "?" PORBASE puts before words to skip when sorting: "?A ?violência" → "A violência". */
internal fun bnpTitle(title: String): String =
    title.replace(Regex("(?:^|(?<=\\s))\\?(?=\\S)"), "").trim()

/** "Cossery, Albert, 1913-2008" → "Albert Cossery"; life dates are dropped. */
internal fun bnpPersonName(heading: String): String {
    val parts = heading.split(",").map { it.trim() }.filter { it.isNotEmpty() && it.none(Char::isDigit) }
    return when (parts.size) {
        0 -> heading.trim()
        1 -> parts[0]
        else -> "${parts[1]} ${parts[0]}"
    }
}

/** "181, [3] p., 21 cm" → 181; "[200] p." → 200. */
private fun pagesFrom(format: String): Int? {
    val beforePages = format.substringBefore(" p.", missingDelimiterValue = "").ifEmpty { return null }
    return Regex("\\d+").find(beforePages)?.value?.toIntOrNull()
}
