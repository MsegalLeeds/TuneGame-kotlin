package com.msegal.tunegame

import com.msegal.tunegame.game.Song
import com.msegal.tunegame.spotify.FakeSpotifyService
import com.msegal.tunegame.spotify.RealSpotifyService
import com.msegal.tunegame.spotify.SpotifyPlaylistMetadata
import com.msegal.tunegame.spotify.SpotifyService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SpotifyUserPlaylistsTest {
    private val songs = listOf(
        Song("Come Together", "Abbey Road", "The Beatles"),
        Song("Money", "The Dark Side of the Moon", "Pink Floyd"),
        Song("Dreams", "Rumours", "Fleetwood Mac"),
        Song("Roxanne", "Outlandos d'Amour", "The Police")
    )

    @Test
    fun `user playlists follow offset pages and ignore null items`() = runTest {
        val offsets = mutableListOf<String?>()
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("https", request.url.protocol.name)
            assertEquals("api.spotify.com", request.url.host)
            assertEquals("/v1/me/playlists", request.url.encodedPath)
            assertEquals("50", request.url.parameters["limit"])
            assertEquals("Bearer user-access-token", request.headers[HttpHeaders.Authorization])
            val offset = request.url.parameters["offset"]
            offsets += offset
            val body = when (offset) {
                "0" -> """{
                    "items":[{"id":"first","name":"First playlist","owner":{"id":"listener"}},null],
                    "next":"https://api.spotify.com/v1/me/playlists?limit=50&offset=50"
                }"""
                "50" -> """{"items":[{"id":"second","name":"Second playlist"}],"next":null}"""
                else -> error("Unexpected offset: $offset")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        spotifyClient(engine).use { client ->
            val playlists = RealSpotifyService(client) { "user-access-token" }.getUserPlaylists()
            assertEquals(listOf(
                SpotifyPlaylistMetadata("first", "First playlist"),
                SpotifyPlaylistMetadata("second", "Second playlist")
            ), playlists)
        }
        assertEquals(listOf<String?>("0", "50"), offsets)
    }

    @Test
    fun `missing access token fails without making a Spotify request`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        spotifyClient(engine).use { client ->
            assertFailsWith<IllegalStateException> {
                RealSpotifyService(client) { null }.getUserPlaylists()
            }
        }
        assertEquals(0, requests)
    }

    @Test
    fun `Spotify HTTP errors fail instead of returning an empty playlist list`() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests, HttpStatusCode.BadGateway)) {
            val engine = MockEngine {
                respond("""{"error":{"message":"upstream failed"}}""", status,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
            spotifyClient(engine).use { client ->
                assertFailsWith<IllegalStateException>(status.toString()) {
                    RealSpotifyService(client) { "token" }.getUserPlaylists()
                }
            }
        }
    }

    @Test
    fun `user playlists endpoint requires a connected Spotify account`() = testApplication {
        application { module(songs = songs, databaseUrl = "jdbc:sqlite::memory:") }
        val visitor = createClient { install(HttpCookies) }

        val response = visitor.get("/spotify/playlists")

        assertEquals(HttpStatusCode.Unauthorized, response.status, response.bodyAsText())
    }

    @Test
    fun `connected user playlists endpoint returns only playlist summaries`() = testApplication {
        val spotify = FakeSpotifyService().apply {
            userPlaylists = mutableListOf(
                SpotifyPlaylistMetadata("mine", "My favourites"),
                SpotifyPlaylistMetadata("shared", "A shared playlist")
            )
        }
        application {
            module(songs = songs, spotifyServiceOverride = spotify, databaseUrl = "jdbc:sqlite::memory:")
        }
        val visitor = createClient { install(HttpCookies) }

        val response = visitor.get("/spotify/playlists")

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val entries = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(listOf("mine", "shared"), entries.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        assertEquals(listOf("My favourites", "A shared playlist"),
            entries.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        entries.forEach { assertEquals(setOf("id", "name"), it.jsonObject.keys) }
    }

    @Test
    fun `upstream user playlist failure returns a generic service error`() = testApplication {
        val spotify = object : SpotifyService by FakeSpotifyService() {
            override suspend fun getUserPlaylists(): List<SpotifyPlaylistMetadata> =
                error("private-upstream-detail token=secret")
        }
        application {
            module(songs = songs, spotifyServiceOverride = spotify, databaseUrl = "jdbc:sqlite::memory:")
        }
        val response = client.get("/spotify/playlists")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains("private-upstream-detail"))
        assertFalse(body.contains("secret"))
        assertTrue(Json.parseToJsonElement(body).jsonObject["error"]!!.jsonPrimitive.content.isNotBlank())
    }

    private fun spotifyClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}
