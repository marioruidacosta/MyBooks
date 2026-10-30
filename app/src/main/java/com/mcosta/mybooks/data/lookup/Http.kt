package com.mcosta.mybooks.data.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal val lookupJson = Json { ignoreUnknownKeys = true }

internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** GETs [url] and returns the body, or null on 404. Throws [IOException] on other failures. */
internal suspend fun OkHttpClient.getOrNull(url: String): String? = withContext(Dispatchers.IO) {
    newCall(Request.Builder().url(url).build()).execute().use { response ->
        when {
            response.code == 404 -> null
            !response.isSuccessful -> throw HttpStatusException(response.code)
            else -> response.body.string()
        }
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

internal fun httpsUrl(url: String?): String? = url?.replaceFirst(Regex("^http://"), "https://")

/** First 4-digit run in [date] as a year, e.g. "June 1983" → 1983, "2005-03-01" → 2005. */
internal fun yearFrom(date: String?): Int? = date?.let { Regex("\\d{4}").find(it)?.value?.toInt() }
