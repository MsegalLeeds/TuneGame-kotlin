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
}