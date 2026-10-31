package com.mcosta.mybooks.data.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

internal val lookupJson = Json { ignoreUnknownKeys = true }

/** HTTP client for the book APIs; identifies the app with [userAgent] as Open Library requests. */
fun lookupHttpClient(userAgent: String): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(15, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
    }
    .build()

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

/** Short, user-facing reason for a lookup failure. */
fun failureReason(cause: Throwable): String = when (cause) {
    is HttpStatusException -> when (cause.code) {
        429 -> "daily request limit reached"
        400, 401, 403 -> "API key rejected"
        else -> "server error (HTTP ${cause.code})"
    }
    is UnknownHostException -> "no internet connection"
    is SocketTimeoutException, is InterruptedIOException -> "request timed out"
    is IOException -> "network error"
    else -> "unexpected response"
}

internal fun httpsUrl(url: String?): String? = url?.replaceFirst(Regex("^http://"), "https://")

/** First 4-digit run in [date] as a year, e.g. "June 1983" → 1983, "2005-03-01" → 2005. */
internal fun yearFrom(date: String?): Int? = date?.let { Regex("\\d{4}").find(it)?.value?.toInt() }
