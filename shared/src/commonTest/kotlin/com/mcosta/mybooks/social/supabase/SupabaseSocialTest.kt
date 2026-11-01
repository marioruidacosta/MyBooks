package com.mcosta.mybooks.social.supabase

import com.mcosta.mybooks.MainDispatcherTest
import com.mcosta.mybooks.social.SocialConfig
import com.mcosta.mybooks.social.SocialException
import com.mcosta.mybooks.social.model.Relationship
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseSocialTest : MainDispatcherTest() {
    private val baseUrl = "https://test.supabase.co"
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    /** Responds by path; each path's responses are used in order. */
    private fun social(routes: Map<String, List<Pair<Int, String>>>): SupabaseSocial {
        val queues = routes.mapValues { ArrayDeque(it.value) }.toMutableMap()
        val engine = MockEngine { request -> respondFor(request, queues) }
        val client = supabaseClient(SocialConfig(baseUrl, "anon-key"), engine, persistSession = false)
        return SupabaseSocial(client, currentUserId = { "me" })
    }

    private fun MockRequestHandleScope.respondFor(
        request: HttpRequestData,
        queues: MutableMap<String, ArrayDeque<Pair<Int, String>>>,
    ) = run {
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        requests += request to body
        val (status, json) = queues[request.url.encodedPath]?.removeFirstOrNull() ?: (200 to "[]")
        respond(json, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @Test
    fun sendRequestCallsRpc() = runTest {
        val social = social(mapOf("/rest/v1/rpc/send_friend_request" to listOf(200 to "\"accepted\"")))
        assertEquals(Relationship.FRIENDS, social.services.friends.sendRequest("id-bruno"))
        val (request, body) = requests.single()
        assertEquals("/rest/v1/rpc/send_friend_request", request.url.encodedPath)
        assertTrue(body.replace(" ", "").contains("\"target\":\"id-bruno\""), body)
    }

    @Test
    fun searchMapsRows() = runTest {
        val social = social(
            mapOf(
                "/rest/v1/rpc/search_profiles" to listOf(
                    200 to """[{"id":"id-bruno","username":"bruno","display_name":"Bruno","avatar_path":null,"visibility":"friends"}]""",
                ),
                "/rest/v1/friendships" to listOf(200 to """[{"requester":"me","addressee":"id-bruno","status":"pending"}]"""),
            )
        )
        val result = social.services.friends.search(" ma ").single()
        assertEquals("bruno", result.person.username)
        assertEquals(Relationship.REQUEST_SENT, result.relationship)
        val rpcBody = requests.first { it.first.url.encodedPath.endsWith("search_profiles") }.second.replace(" ", "")
        assertTrue(rpcBody.contains("\"query\":\"ma\"") && rpcBody.contains("\"max\":20"), rpcBody)
    }

    @Test
    fun shortSearchMakesNoRequest() = runTest {
        assertTrue(social(emptyMap()).services.friends.search("m").isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun usernameTakenMapsToUsernameTaken() = runTest {
        val social = social(
            mapOf(
                "/rest/v1/profiles" to listOf(
                    409 to """{"code":"23505","message":"duplicate key value violates unique constraint \"profiles_username_key\"","details":null,"hint":null}""",
                ),
            )
        )
        val e = assertFailsWith<SocialException> { social.services.profiles.createProfile("bruno", "Bruno") }
        assertEquals(SocialException.Kind.USERNAME_TAKEN, e.kind)
    }

    @Test
    fun networkFailureIsOffline() = runTest {
        val engine = MockEngine { throw IOException("no route to host") }
        val social = SupabaseSocial(supabaseClient(SocialConfig(baseUrl, "anon-key"), engine, persistSession = false), currentUserId = { "me" })
        val e = assertFailsWith<SocialException> { social.services.friends.friends() }
        assertEquals(SocialException.Kind.OFFLINE, e.kind)
    }

    @Test
    fun avatarUrlBuiltFromPath() = runTest {
        val social = social(
            mapOf(
                "/rest/v1/profiles" to listOf(
                    200 to """[{"id":"id-bruno","username":"bruno","display_name":"Bruno","avatar_path":"id-bruno/avatar-1.jpg","visibility":"public"}]""",
                ),
            )
        )
        assertEquals(
            "$baseUrl/storage/v1/object/public/avatars/id-bruno/avatar-1.jpg",
            social.services.profiles.person("id-bruno")?.avatarUrl,
        )
    }

    @Test
    fun hiddenPersonIsNull() = runTest {
        val social = social(mapOf("/rest/v1/profiles" to listOf(200 to "[]")))
        assertEquals(null, social.services.profiles.person("id-blocker"))
    }

    @Test
    fun disabledConfigMeansNoSocial() {
        assertFalse(SocialConfig("", "").enabled)
        assertFalse(SocialConfig("https://x.supabase.co", " ").enabled)
        assertTrue(SocialConfig("https://x.supabase.co", "key").enabled)
    }
}
