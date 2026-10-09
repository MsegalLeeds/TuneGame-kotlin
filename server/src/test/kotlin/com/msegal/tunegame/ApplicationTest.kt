package com.msegal.tunegame

import com.msegal.tunegame.game.Song
import com.msegal.tunegame.spotify.FakeSpotifyService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    private val testSongs = listOf(
        Song(
            "Come Together",
            "Abbey Road",
            "The Beatles"
        ),
        Song(
            "Money",
            "The Dark Side of the Moon",
            "Pink Floyd"
        ),
        Song(
            "Dreams",
            "Rumours",
            "Fleetwood Mac"
        ),
        Song(
            "Roxanne",
            "Outlandos d'Amour",
            "The Police"
        )
    )

    @Test
    fun `server root responds`() = testApplication {

        application {
            module(
                songs = testSongs,
                spotifyServiceOverride =
                    FakeSpotifyService(),
                databaseUrl = "jdbc:sqlite::memory:"
            )
        }

        val response =
            client.get("/")

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
            module(
                songs = testSongs,
                spotifyServiceOverride =
                    FakeSpotifyService(),
                databaseUrl = "jdbc:sqlite::memory:"
            )
        }

        val response =
            client.post("/new-game")

        assertEquals(
            HttpStatusCode.OK,
            response.status
        )

        val body =
            response.bodyAsText()

        assertTrue(
            body.contains("\"ok\":true")
        )

        assertTrue(
            body.contains("\"gameId\"")
        )

        assertTrue(
            body.contains("\"lives\":3")
        )
    }

    @Test
    fun `question without game id returns bad request`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val response =
                client.get("/question")

            assertEquals(
                HttpStatusCode.BadRequest,
                response.status
            )

            assertTrue(
                response.bodyAsText()
                    .contains(
                        "gameId is required"
                    )
            )
        }

    @Test
    fun `invalid game id returns not found`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val response =
                client.get(
                    "/question?gameId=not-a-real-game"
                )

            assertEquals(
                HttpStatusCode.NotFound,
                response.status
            )

            assertTrue(
                response.bodyAsText()
                    .contains(
                        "Game not found"
                    )
            )
        }

    @Test
    fun `question response does not expose correct answer`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            /*
             * Create a game.
             */
            val newGameResponse =
                client.post("/new-game")

            assertEquals(
                HttpStatusCode.OK,
                newGameResponse.status
            )

            val newGameBody =
                newGameResponse.bodyAsText()

            /*
             * Extract the generated game ID.
             */
            val gameId =
                Regex(
                    "\"gameId\":\"([^\"]+)\""
                )
                    .find(newGameBody)
                    ?.groupValues
                    ?.get(1)

            assertTrue(
                gameId != null
            )

            /*
             * Request a question.
             */
            val questionResponse =
                client.get(
                    "/question?gameId=$gameId"
                )

            assertEquals(
                HttpStatusCode.OK,
                questionResponse.status
            )

            val body =
                questionResponse.bodyAsText()

            /*
             * Public information should be present.
             */
            assertTrue(
                body.contains(
                    "\"question\""
                )
            )

            assertTrue(
                body.contains(
                    "\"choices\""
                )
            )

            assertTrue(
                body.contains(
                    "\"type\""
                )
            )

            /*
             * The answer must remain server-side.
             */
            assertFalse(
                body.contains(
                    "\"correctAnswer\""
                )
            )
        }

    @Test
    fun `playlist can be created`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val response =
                client.post("/playlist") {

                    contentType(
                        ContentType.Application.Json
                    )

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

            val body =
                response.bodyAsText()

            /*
             * The backend uses the Spotify
             * playlist name returned by
             * FakeSpotifyService.
             */
            assertTrue(
                body.contains(
                    "\"name\":\"Test Playlist\""
                )
            )

            assertTrue(
                body.contains(
                    "\"spotifyPlaylistId\":\"spotify123\""
                )
            )
        }

    @Test
    fun `playlist endpoint lists created playlists`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val createResponse =
                client.post("/playlist") {

                    contentType(
                        ContentType.Application.Json
                    )

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
                createResponse.status
            )

            val response =
                client.get("/playlist")

            assertEquals(
                HttpStatusCode.OK,
                response.status
            )

            assertTrue(
                response.bodyAsText()
                    .contains(
                        "\"name\":\"Test Playlist\""
                    )
            )

            assertTrue(
                response.bodyAsText()
                    .contains(
                        "\"spotifyPlaylistId\":\"spotify123\""
                    )
            )
        }

    @Test
    fun `playlist with blank name is rejected`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val response =
                client.post("/playlist") {

                    contentType(
                        ContentType.Application.Json
                    )

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
    fun `unfinished game score cannot be submitted`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val newGameResponse =
                client.post("/new-game")

            val body =
                newGameResponse.bodyAsText()

            val gameId =
                Regex(
                    "\"gameId\":\"([^\"]+)\""
                )
                    .find(body)
                    ?.groupValues
                    ?.get(1)

            assertTrue(
                gameId != null
            )

            val response =
                client.post("/scores") {

                    contentType(
                        ContentType.Application.Json
                    )

                    setBody(
                        """
                        {
                            "playerName": "Marc",
                            "gameId": "$gameId"
                        }
                        """.trimIndent()
                    )
                }

            assertEquals(
                HttpStatusCode.Conflict,
                response.status
            )

            assertTrue(
                response.bodyAsText()
                    .contains(
                        "Game is not over"
                    )
            )
        }

    @Test
    fun `score submission rejects unknown game`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val response =
                client.post("/scores") {

                    contentType(
                        ContentType.Application.Json
                    )

                    setBody(
                        """
                        {
                            "playerName": "Marc",
                            "gameId": "fake-game-id"
                        }
                        """.trimIndent()
                    )
                }

            assertEquals(
                HttpStatusCode.NotFound,
                response.status
            )
        }

    @Test
    fun `negative score is rejected`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            val response =
                client.post("/scores") {

                    contentType(
                        ContentType.Application.Json
                    )

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

    @Test
    fun `finished game score can only be submitted once`() =
        testApplication {

            application {
                module(
                    songs = testSongs,
                    spotifyServiceOverride =
                        FakeSpotifyService(),
                    databaseUrl = "jdbc:sqlite::memory:"
                )
            }

            /*
             * 1. Create a new game.
             */
            val newGameResponse =
                client.post("/new-game")

            assertEquals(
                HttpStatusCode.OK,
                newGameResponse.status
            )

            val newGameBody =
                newGameResponse.bodyAsText()

            val gameId =
                Regex(
                    "\"gameId\":\"([^\"]+)\""
                )
                    .find(newGameBody)
                    ?.groupValues
                    ?.get(1)

            assertTrue(
                gameId != null
            )

            /*
             * 2. Lose all three lives.
             */
            repeat(3) {

                val questionResponse =
                    client.get(
                        "/question?gameId=$gameId"
                    )

                assertEquals(
                    HttpStatusCode.OK,
                    questionResponse.status
                )

                val answerResponse =
                    client.post("/answer") {

                        contentType(
                            ContentType.Application.Json
                        )

                        setBody(
                            """
                            {
                                "gameId": "$gameId",
                                "answer": "THIS IS DEFINITELY NOT THE ANSWER"
                            }
                            """.trimIndent()
                        )
                    }

                assertEquals(
                    HttpStatusCode.OK,
                    answerResponse.status
                )
            }

            /*
             * 3. The finished game's score
             * can now be submitted.
             */
            val firstSubmission =
                client.post("/scores") {

                    contentType(
                        ContentType.Application.Json
                    )

                    setBody(
                        """
                        {
                            "playerName": "Marc",
                            "gameId": "$gameId"
                        }
                        """.trimIndent()
                    )
                }

            assertEquals(
                HttpStatusCode.Created,
                firstSubmission.status
            )

            val firstBody =
                firstSubmission.bodyAsText()

            assertTrue(
                firstBody.contains(
                    "\"playerName\":\"Marc\""
                )
            )

            /*
             * All answers were wrong,
             * so the score should be zero.
             */
            assertTrue(
                firstBody.contains(
                    "\"score\":0"
                )
            )

            /*
             * 4. Try to submit the
             * same game again.
             */
            val secondSubmission =
                client.post("/scores") {

                    contentType(
                        ContentType.Application.Json
                    )

                    setBody(
                        """
                        {
                            "playerName": "Marc Again",
                            "gameId": "$gameId"
                        }
                        """.trimIndent()
                    )
                }

            assertEquals(
                HttpStatusCode.Conflict,
                secondSubmission.status
            )

            assertTrue(
                secondSubmission
                    .bodyAsText()
                    .contains(
                        "Score has already been submitted"
                    )
            )

            /*
             * 5. Make sure only one
             * leaderboard entry exists.
             */
            val leaderboard =
                client.get("/scores")

            assertEquals(
                HttpStatusCode.OK,
                leaderboard.status
            )

            val leaderboardBody =
                leaderboard.bodyAsText()

            assertTrue(
                leaderboardBody.contains(
                    "\"playerName\":\"Marc\""
                )
            )

            assertFalse(
                leaderboardBody.contains(
                    "\"playerName\":\"Marc Again\""
                )
            )
        }
}