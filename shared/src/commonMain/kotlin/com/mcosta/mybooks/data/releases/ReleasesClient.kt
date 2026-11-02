package com.mcosta.mybooks.data.releases

import com.mcosta.mybooks.data.lookup.getOrNull
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

/** The lists aren't available: not published yet ([notPublished]), or no network and no saved copy. */
class ReleasesUnavailable(val notPublished: Boolean, cause: Throwable? = null) : Exception(cause)

/** Downloads the releases job's files from GitHub Pages, as raw JSON. */
class ReleasesClient(private val http: HttpClient, private val baseUrl: String = RELEASES_BASE_URL) {
    suspend fun index(): String = get("index.json")

    suspend fun month(month: String): String = get("$month.json")

    private suspend fun get(name: String): String = try {
        http.getOrNull(baseUrl + name) ?: throw ReleasesUnavailable(notPublished = true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ReleasesUnavailable) {
        throw e
    } catch (e: Exception) {
        throw ReleasesUnavailable(notPublished = false, cause = e)
    }
}
