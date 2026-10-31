package com.mcosta.mybooks.data.lookup

import com.mcosta.mybooks.domain.Book
import okhttp3.OkHttpClient
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Biblioteca Nacional de Portugal (PORBASE) URN resolver, which returns Dublin Core XML for an ISBN.
 * A missing record is HTTP 200 with `<urn-response><error>Registo inexistente</error></urn-response>`.
 */
class BnpClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://urn.porbase.org",
) : BookLookupService {

    override suspend fun lookup(isbn: String): LookupResult = lookupCatching {
        val body = http.getOrNull("$baseUrl/isbn/dc/xml?id=$isbn") ?: return LookupResult.NotFound
        val root = parseXml(body)
        if (root.localName != "dc") return LookupResult.NotFound
        fun values(name: String): List<String> {
            val nodes = root.getElementsByTagNameNS(DC_NS, name)
            return (0 until nodes.length).map { cleanText(nodes.item(it).textContent) }.filter { it.isNotEmpty() }
        }
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

    private fun parseXml(text: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(text))).documentElement
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
