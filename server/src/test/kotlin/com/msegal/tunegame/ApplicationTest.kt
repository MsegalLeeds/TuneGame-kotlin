package com.msegal.tunegame

import com.msegal.tunegame.game.Song
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import kotlin.test.assertFalse

class ApplicationTest {

    private val testSongs = listOf(
        Song("Come Together", "Abbey Road", "The Beatles"),
        Song("Money", "The Dark Side of the Moon", "Pink Floyd"),
        Song("Dreams", "Rumours", "Fleetwood Mac"),
        Song("Roxanne", "Outlandos d'Amour", "The Police")
    )

    @Test
    fun `server root responds`() = testApplication {

        application {
            module(testSongs)
        }

        val response = client.get("/")

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )

        assertEquals(
            "TuneGame server is running!",
            response.bodyAsText()
        )
    }

    @Test
    fun `new game creates game`() = testApplication {

        application {
            module(testSongs)
        }

        val response = client.post("/new-game")

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )

        val body = response.bodyAsText()

        assertTrue(body.contains("\"ok\":true"))
        assertTrue(body.contains("\"gameId\""))
        assertTrue(body.contains("\"lives\":3"))
    }

    @Test
    fun `question without game id returns bad request`() = testApplication {

        application {
            module(testSongs)
        }

        val response = client.get("/question")

        assertEquals(
            HttpStatusCode.BadRequest,
            response.status
        )

        assertTrue(
            response.bodyAsText()
                .contains("gameId is required")
        )
    }

    @Test
    fun `invalid game id returns not found`() = testApplication {

        application {
            module(testSongs)
        }

        val response = client.get(
            "/question?gameId=not-a-real-game"
        )

        assertEquals(
            HttpStatusCode.NotFound,
            response.status
        )

        assertTrue(
            response.bodyAsText()
                .contains("Game not found")
        )
    }

    @Test
    fun `question response does not expose correct answer`() = testApplication {

        application {
            module(testSongs)
        }

        // Create a game
        val newGameResponse = client.post("/new-game")

        assertEquals(
            HttpStatusCode.OK,
            newGameResponse.status
        )

        val newGameBody = newGameResponse.bodyAsText()

        // Extract the generated game ID
        val gameId = Regex("\"gameId\":\"([^\"]+)\"")
            .find(newGameBody)
            ?.groupValues
            ?.get(1)

        assertTrue(gameId != null)

        // Request a question
        val questionResponse = client.get(
            "/question?gameId=$gameId"
        )

        assertEquals(
            HttpStatusCode.OK,
            questionResponse.status
        )

        val body = questionResponse.bodyAsText()

        // Public information should be present
        assertTrue(body.contains("\"question\""))
        assertTrue(body.contains("\"choices\""))
        assertTrue(body.contains("\"type\""))

        // The answer must remain server-side
        assertFalse(body.contains("\"correctAnswer\""))
    }

    @Test
    fun `playlist can be created`() = testApplication {

        application {
            module(testSongs)
        }

        val response = client.post("/playlist") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "Classic Rock",
                "spotifyPlaylistId": "spotify123"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Created,
            response.status
        )

        val body = response.bodyAsText()

        assertTrue(
            body.contains("\"name\":\"Classic Rock\"")
        )

        assertTrue(
            body.contains(
                "\"spotifyPlaylistId\":\"spotify123\""
            )
        )
    }

    @Test
    fun `playlist endpoint lists created playlists`() = testApplication {

        application {
            module(testSongs)
        }

        client.post("/playlist") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "Classic Rock",
                "spotifyPlaylistId": "spotify123"
            }
            """.trimIndent()
            )
        }

        val response =
            client.get("/playlist")

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )

        assertTrue(
            response.bodyAsText()
                .contains("\"name\":\"Classic Rock\"")
        )
    }

    @Test
    fun `playlist with blank name is rejected`() = testApplication {

        application {
            module(testSongs)
        }

        val response = client.post("/playlist") {
            contentType(ContentType.Application.Json)

            setBody(
                """
            {
                "name": "",
                "spotifyPlaylistId": "spotify123"
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.BadRequest,
            response.status
        )
    }
    @Test
    fun `score can be submitted`() = testApplication {
        application {
            module(testSongs)
        }

        val response = client.post("/scores") {
            contentType(ContentType.Application.Json)
            setBody(
                """
            {
                "playerName": "Marc",
                "score": 150
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.Created,
            response.status
        )

        val body = response.bodyAsText()

        assertTrue(body.contains("\"playerName\":\"Marc\""))
        assertTrue(body.contains("\"score\":150"))
    }

    @Test
    fun `scores are returned highest first`() = testApplication {
        application {
            module(testSongs)
        }

        suspend fun submitScore(
            name: String,
            score: Int
        ) {
            client.post("/scores") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                {
                    "playerName": "$name",
                    "score": $score
                }
                """.trimIndent()
                )
            }
        }

        submitScore("Low", 50)
        submitScore("High", 200)
        submitScore("Middle", 100)

        val response = client.get("/scores")

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )

        val body = response.bodyAsText()

        val highPosition =
            body.indexOf("\"playerName\":\"High\"")

        val middlePosition =
            body.indexOf("\"playerName\":\"Middle\"")

        val lowPosition =
            body.indexOf("\"playerName\":\"Low\"")

        assertTrue(highPosition >= 0)
        assertTrue(middlePosition >= 0)
        assertTrue(lowPosition >= 0)

        assertTrue(highPosition < middlePosition)
        assertTrue(middlePosition < lowPosition)
    }

    @Test
    fun `negative score is rejected`() = testApplication {
        application {
            module(testSongs)
        }

        val response = client.post("/scores") {
            contentType(ContentType.Application.Json)
            setBody(
                """
            {
                "playerName": "Marc",
                "score": -100
            }
            """.trimIndent()
            )
        }

        assertEquals(
            HttpStatusCode.BadRequest,
            response.status
        )
    }
}