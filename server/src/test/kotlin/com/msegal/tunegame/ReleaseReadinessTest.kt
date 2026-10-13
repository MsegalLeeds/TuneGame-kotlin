package com.msegal.tunegame

import com.msegal.tunegame.game.Song
import com.msegal.tunegame.session.UserSession
import com.msegal.tunegame.spotify.FakeSpotifyService
import com.msegal.tunegame.spotify.SpotifyService
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.testing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import kotlin.test.*

class ReleaseReadinessTest {
    private val songs = listOf(
        Song("Come Together", "Abbey Road", "The Beatles"),
        Song("Money", "The Dark Side of the Moon", "Pink Floyd"),
        Song("Dreams", "Rumours", "Fleetwood Mac"),
        Song("Roxanne", "Outlandos d'Amour", "The Police")
    )

    @Test
    fun `default game can finish and submit score without Spotify configuration`() = testApplication {
        application { module(songs = songs, databaseUrl = "jdbc:sqlite::memory:") }
        val player = createClient { install(HttpCookies) }

        val created = player.post("/new-game")
        assertEquals(HttpStatusCode.OK, created.status)
        val gameId = created.json()["gameId"]!!.jsonPrimitive.content

        repeat(3) { round ->
            val question = player.get("/question?gameId=$gameId")
            assertEquals(HttpStatusCode.OK, question.status, question.bodyAsText())
            val publicQuestion = question.json()["question"]!!.jsonObject
            assertEquals(4, publicQuestion["choices"]!!.jsonArray.size)
            assertFalse("correctAnswer" in publicQuestion)

            val answer = player.post("/answer") {
                contentType(ContentType.Application.Json)
                setBody("""{"gameId":"$gameId","answer":"Definitely not an answer"}""")
            }
            assertEquals(HttpStatusCode.OK, answer.status, answer.bodyAsText())
            val result = answer.json()
            assertFalse(result["correct"]!!.jsonPrimitive.boolean)
            assertEquals(2 - round, result["lives"]!!.jsonPrimitive.int)
            assertEquals(round == 2, result["gameOver"]!!.jsonPrimitive.boolean)
        }

        val submitted = player.post("/scores") {
            contentType(ContentType.Application.Json)
            setBody("""{"gameId":"$gameId","playerName":"Release Player"}""")
        }
        assertEquals(HttpStatusCode.Created, submitted.status, submitted.bodyAsText())
        assertEquals(0, submitted.json()["score"]!!.jsonPrimitive.int)

        val leaderboard = player.get("/scores")
        assertEquals(HttpStatusCode.OK, leaderboard.status)
        val entries = Json.parseToJsonElement(leaderboard.bodyAsText()).jsonArray
        assertEquals(1, entries.size)
        assertEquals("Release Player", entries.single().jsonObject["playerName"]!!.jsonPrimitive.content)
    }

    @Test
    fun `malformed new game JSON is rejected instead of silently creating a game`() = testApplication {
        application { module(songs = songs, databaseUrl = "jdbc:sqlite::memory:") }

        val response = client.post("/new-game") {
            contentType(ContentType.Application.Json)
            setBody("""{"playlistId": """)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        assertFalse(response.bodyAsText().contains("\"gameId\""))
    }

    @Test
    fun `unsigned session cookie cannot choose the user session id`() = testApplication {
        application { module(songs = songs, databaseUrl = "jdbc:sqlite::memory:") }
        val forgedId = "11111111-1111-4111-8111-111111111111"
        val forgedCookie = renderCookieHeader(
            Cookie("tunegame_session", defaultSessionSerializer<UserSession>().serialize(UserSession(forgedId)))
        )

        val response = client.get("/session") {
            header(HttpHeaders.Cookie, forgedCookie)
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val issuedId = response.json()["sessionId"]!!.jsonPrimitive.content
        assertNotEquals(forgedId, issuedId)
        assertTrue(issuedId.isNotBlank())
        val cookie = assertNotNull(response.headers[HttpHeaders.SetCookie]).substringBefore(';')
        val resumed = client.get("/session") { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.OK, resumed.status)
        assertEquals(issuedId, resumed.json()["sessionId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `simultaneous question requests activate exactly one question`() = testApplication {
        application { module(songs = songs, databaseUrl = "jdbc:sqlite::memory:") }
        val player = createClient { install(HttpCookies) }
        player.get("/session")

        repeat(8) {
            val created = player.post("/new-game")
            assertEquals(HttpStatusCode.OK, created.status)
            val gameId = created.json()["gameId"]!!.jsonPrimitive.content
            val statuses = coroutineScope {
                val start = CompletableDeferred<Unit>()
                val requests = List(16) {
                    async(Dispatchers.Default) {
                        start.await()
                        player.get("/question?gameId=$gameId").status
                    }
                }
                start.complete(Unit)
                requests.awaitAll()
            }

            assertEquals(1, statuses.count { it == HttpStatusCode.OK }, statuses.toString())
            assertEquals(15, statuses.count { it == HttpStatusCode.Conflict }, statuses.toString())
        }
    }

    @Test
    fun `score names longer than twenty characters are rejected`() = testApplication {
        application { module(songs = songs, databaseUrl = "jdbc:sqlite::memory:") }

        val response = client.post("/scores") {
            contentType(ContentType.Application.Json)
            setBody("""{"gameId":"unused","playerName":"${"a".repeat(21)}"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
    }

    @Test
    fun `another visitor cannot question answer or submit the owner's game`() = testApplication {
        application { module(songs = songs, databaseUrl = "jdbc:sqlite::memory:") }
        val owner = createClient { install(HttpCookies) }
        val visitor = createClient { install(HttpCookies) }
        val created = owner.post("/new-game")
        assertEquals(HttpStatusCode.OK, created.status)
        val gameId = created.json()["gameId"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.NotFound, visitor.get("/question?gameId=$gameId").status)
        assertEquals(HttpStatusCode.OK, owner.get("/question?gameId=$gameId").status)
        val stolenAnswer = visitor.post("/answer") {
            contentType(ContentType.Application.Json)
            setBody("""{"gameId":"$gameId","answer":"Wrong answer"}""")
        }
        assertEquals(HttpStatusCode.NotFound, stolenAnswer.status)

        repeat(3) { round ->
            if (round > 0) {
                assertEquals(HttpStatusCode.OK, owner.get("/question?gameId=$gameId").status)
            }
            val answer = owner.post("/answer") {
                contentType(ContentType.Application.Json)
                setBody("""{"gameId":"$gameId","answer":"Wrong answer"}""")
            }
            assertEquals(HttpStatusCode.OK, answer.status)
            assertEquals(2 - round, answer.json()["lives"]!!.jsonPrimitive.int)
        }

        val stolenScore = visitor.post("/scores") {
            contentType(ContentType.Application.Json)
            setBody("""{"gameId":"$gameId","playerName":"Intruder"}""")
        }
        assertEquals(HttpStatusCode.NotFound, stolenScore.status)
        val ownScore = owner.post("/scores") {
            contentType(ContentType.Application.Json)
            setBody("""{"gameId":"$gameId","playerName":"Owner"}""")
        }
        assertEquals(HttpStatusCode.Created, ownScore.status)
    }

    @Test
    fun `saved playlists are private to the visitor who created them`() = testApplication {
        val spotify = object : SpotifyService by FakeSpotifyService() {
            override suspend fun getPlaylistSongs(playlistId: String): List<Song> = songs
        }
        application {
            module(songs = songs, spotifyServiceOverride = spotify, databaseUrl = "jdbc:sqlite::memory:")
        }
        val owner = createClient { install(HttpCookies) }
        val visitor = createClient { install(HttpCookies) }
        val created = owner.post("/playlist") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Private playlist","spotifyPlaylistId":"spotify123"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val playlistId = created.json()["id"]!!.jsonPrimitive.content

        val visitorList = visitor.get("/playlist")
        assertEquals(HttpStatusCode.OK, visitorList.status)
        assertTrue(Json.parseToJsonElement(visitorList.bodyAsText()).jsonArray.isEmpty())
        assertEquals(HttpStatusCode.NotFound, visitor.delete("/playlist/$playlistId").status)
        val stolenGame = visitor.post("/new-game") {
            contentType(ContentType.Application.Json)
            setBody("""{"playlistId":"$playlistId"}""")
        }
        assertEquals(HttpStatusCode.NotFound, stolenGame.status)

        val ownerList = owner.get("/playlist")
        assertEquals(HttpStatusCode.OK, ownerList.status)
        assertEquals(playlistId, Json.parseToJsonElement(ownerList.bodyAsText())
            .jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        val ownGame = owner.post("/new-game") {
            contentType(ContentType.Application.Json)
            setBody("""{"playlistId":"$playlistId"}""")
        }
        assertEquals(HttpStatusCode.OK, ownGame.status)
        assertEquals(HttpStatusCode.NoContent, owner.delete("/playlist/$playlistId").status)
    }

    private suspend fun HttpResponse.json(): JsonObject =
        Json.parseToJsonElement(bodyAsText()).jsonObject
}
