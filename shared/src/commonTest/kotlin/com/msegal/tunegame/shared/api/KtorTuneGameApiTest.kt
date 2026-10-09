package com.msegal.tunegame.shared.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorTuneGameApiTest {

    private fun createClient(
        handler: MockRequestHandler
    ): HttpClient {

        val engine = MockEngine(handler)

        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
        }
    }

    @Test
    fun `new game returns decoded response`() = runTest {

        val client = createClient {
            respond(
                content = """
                    {
                        "ok": true,
                        "message": "Game created",
                        "gameId": "game-123",
                        "lives": 3
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            )
        }

        val api = KtorTuneGameApi(
            baseUrl = "http://localhost:8080",
            client = client
        )

        val result =
            api.newGame()

        assertEquals(true, result.ok)
        assertEquals("game-123", result.gameId)
        assertEquals(3, result.lives)
    }

    @Test
    fun `get question sends game id`() = runTest {

        var requestedGameId: String? = null

        val client = createClient { request ->

            requestedGameId =
                request.url.parameters["gameId"]

            respond(
                content = """
                    {
                        "question": {
                            "question": "Name the artist?",
                            "choices": [
                                "The Beatles",
                                "Pink Floyd",
                                "The Who",
                                "Queen"
                            ],
                            "type": "SONG_TO_ARTIST"
                        }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            )
        }

        val api = KtorTuneGameApi(
            baseUrl = "http://localhost:8080",
            client = client
        )

        val response =
            api.getQuestion("game-123")

        assertEquals(
            "game-123",
            requestedGameId
        )

        assertEquals(
            "Name the artist?",
            response.question.question
        )

        assertEquals(
            QuestionType.SONG_TO_ARTIST,
            response.question.type
        )
    }

    @Test
    fun `submit answer returns game result`() = runTest {

        val client = createClient {
            respond(
                content = """
                    {
                        "correct": true,
                        "correctAnswer": "The Beatles",
                        "pointsAwarded": 10,
                        "score": 10,
                        "streak": 1,
                        "lives": 3,
                        "gameOver": false
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            )
        }

        val api = KtorTuneGameApi(
            baseUrl = "http://localhost:8080",
            client = client
        )

        val result =
            api.submitAnswer(
                gameId = "game-123",
                answer = "The Beatles"
            )

        assertEquals(true, result.correct)
        assertEquals(10, result.pointsAwarded)
        assertEquals(10, result.score)
        assertEquals(1, result.streak)
        assertEquals(3, result.lives)
        assertEquals(false, result.gameOver)
    }
}