package com.mcosta.mybooks.data.lookup

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.io.IOException

internal val lookupJson = Json { ignoreUnknownKeys = true }

/** HTTP client for the book APIs; identifies the app with [userAgent] as Open Library requests. */
fun lookupHttpClient(engine: HttpClientEngine, userAgent: String): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    install(UserAgent) { agent = userAgent }
}

internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** GETs [url] and returns the body, or null on 404. Throws [IOException] on other failures. */
internal suspend fun HttpClient.getOrNull(url: String): String? {
    val response = get(url)
    return when {
        response.status.value == 404 -> null
        !response.status.isSuccess() -> throw HttpStatusException(response.status.value)
        else -> response.bodyAsText()
    }
}

/** Runs [block], turning any failure into [LookupResult.Error]. */
internal inline fun lookupCatching(block: () -> LookupResult): LookupResult =
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        LookupResult.Error(e)
    }

/** Short, user-facing reason for a lookup failure. */
fun failureReason(cause: Throwable): String = when (cause) {
    is HttpStatusException -> when (cause.code) {
        429 -> "daily request limit reached"
        400, 401, 403 -> "API key rejected"
        else -> "server error (HTTP ${cause.code})"
    }
    is HttpRequestTimeoutException -> "request timed out"
    else -> platformNetworkReason(cause) ?: if (cause is IOException) "network error" else "unexpected response"
}

/** Platform-specific network failures (offline, timeouts) as a user-facing reason, or null. */
internal expect fun platformNetworkReason(cause: Throwable): String?

/** Removes control characters (catalogues embed e.g. U+0098/U+009C around non-sorting articles) and extra spaces. */
internal fun cleanText(text: String): String =
    text.filterNot { it.isISOControl() }.replace(Regex("\\s+"), " ").trim()

internal fun httpsUrl(url: String?): String? = url?.replaceFirst(Regex("^http://"), "https://")

/** First 4-digit run in [date] as a year, e.g. "June 1983" → 1983, "2005-03-01" → 2005. */
internal fun yearFrom(date: String?): Int? = date?.let { Regex("\\d{4}").find(it)?.value?.toInt() }
