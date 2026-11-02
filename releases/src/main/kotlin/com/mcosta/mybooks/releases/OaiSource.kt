package com.mcosta.mybooks.releases

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

const val OAI_BASE = "https://oai.bnportugal.gov.pt/servlet/OAIHandler"
const val USER_AGENT = "MyBooks-releases/1.0 (+https://github.com/marioruidacosta/MyBooks)"

/** One ListRecords page of the National Bibliography set, for a date window or a resumption token. */
interface OaiSource {
    fun page(from: LocalDate, until: LocalDate, resumptionToken: String?): OaiPage
}

/**
 * The National Library's OAI-PMH service: one request at a time, [delayMillis] apart, identifying the job.
 * Redirects are followed; [timeout] covers the whole response, body included.
 */
class HttpOaiSource(
    private val baseUrl: String = OAI_BASE,
    private val userAgent: String = USER_AGENT,
    private val delayMillis: Long = 2000,
    private val timeout: Duration = Duration.ofSeconds(120),
) : OaiSource {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private var requests = 0

    override fun page(from: LocalDate, until: LocalDate, resumptionToken: String?): OaiPage {
        if (requests++ > 0) Thread.sleep(delayMillis)
        val query = if (resumptionToken != null) {
            "verb=ListRecords&resumptionToken=${URLEncoder.encode(resumptionToken, Charsets.UTF_8)}"
        } else {
            "verb=ListRecords&set=bibliografianacional&metadataPrefix=marcxchange&from=$from&until=$until"
        }
        val request = HttpRequest.newBuilder(URI("$baseUrl?$query"))
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .GET()
            .build()
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        val response = try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw IOException("OAI request timed out after $timeout for $query", e)
        } catch (e: ExecutionException) {
            throw (e.cause as? IOException) ?: IOException("OAI request failed for $query", e.cause)
        }
        if (response.statusCode() != 200) throw IOException("OAI request failed: HTTP ${response.statusCode()} for $query")
        return OaiParser.parse(response.body().inputStream())
    }
}
