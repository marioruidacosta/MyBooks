package com.mcosta.mybooks.data.lookup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/lookup/$name")) { "missing fixture $name" }.readText()

fun json(name: String): FakeResponse = FakeResponse().setHeader("Content-Type", "application/json").setBody(fixture(name))

/** A canned HTTP response for [FakeHttpServer]. */
class FakeResponse {
    var code = 200
        private set
    var body = ""
        private set
    val headers = mutableMapOf<String, String>()

    fun setResponseCode(code: Int) = apply { this.code = code }
    fun setBody(body: String) = apply { this.body = body }
    fun setHeader(name: String, value: String) = apply { headers[name] = value }
}

class RecordedRequest(val path: String, private val headers: Headers) {
    fun getHeader(name: String): String? = headers[name]
}

/** In-memory HTTP server on Ktor's MockEngine: queue responses, then inspect the requests made. */
class FakeHttpServer {
    private val responses = ArrayDeque<FakeResponse>()
    private val requests = ArrayDeque<RecordedRequest>()

    val baseUrl = "http://fake.test"

    val engine = MockEngine { request ->
        requests.addLast(RecordedRequest(request.url.encodedPathAndQuery, request.headers))
        val r = responses.removeFirst()
        respond(r.body, HttpStatusCode.fromValue(r.code), headersOf(*r.headers.map { it.key to listOf(it.value) }.toTypedArray()))
    }

    fun enqueue(response: FakeResponse) = responses.addLast(response)

    fun takeRequest(): RecordedRequest = requests.removeFirst()

    fun httpClient(userAgent: String = "MyBooks-test"): HttpClient = lookupHttpClient(engine, userAgent)
}
