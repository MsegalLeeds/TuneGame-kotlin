package com.msegal.tunegame.shared.game

import com.msegal.tunegame.shared.api.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameClientTest {

    private class FakeTuneGameApi : TuneGameApi {

        var submittedPlayerName: String? = null
        var submittedGameId: String? = null

        override suspend fun newGame(playlistId: String?): NewGameResponse {
            return NewGameResponse(
                ok = true,
                message = "Game created",
                gameId = "game-123",
                lives = 3
            )
        }

        override suspend fun getQuestion(
            gameId: String
        ): QuestionResponse {
            return QuestionResponse(
                question = PublicQuestion(
                    question = "Who wrote Come Together?",
                    choices = listOf(
                        "The Beatles",
                        "Pink Floyd",
                        "Queen",
                        "The Who"
                    ),
                    type = QuestionType.SONG_TO_ARTIST
                )
            )
        }

        override suspend fun submitAnswer(
            gameId: String,
            answer: String
        ): AnswerResult {
            return AnswerResult(
                correct = true,
                correctAnswer = "The Beatles",
                pointsAwarded = 10,
                score = 10,
                streak = 1,
                lives = 3,
                gameOver = false
            )
        }

        override suspend fun getPlaylists(): List<Playlist> {
            return emptyList()
        }

        override suspend fun createPlaylist(
            name: String,
            spotifyPlaylistId: String
        ): Playlist {
            error("Not used")
        }

        override suspend fun deletePlaylist(
            id: String
        ) {
        }

        override suspend fun getScores(): List<Score> {
            return emptyList()
        }

        override suspend fun submitScore(
            playerName: String,
            gameId: String
        ): Score {
            submittedPlayerName = playerName
            submittedGameId = gameId

            return Score(
                id = "score-1",
                playerName = playerName,
                score = 10
            )
        }
    }

    @Test
    fun `new game resets state`() = runTest {
        val api = FakeTuneGameApi()
        val client = GameClient(api)

        val state = client.newGame()

        assertEquals("game-123", state.gameId)
        assertEquals(3, state.lives)
        assertEquals(0, state.score)
        assertEquals(0, state.streak)
        assertFalse(state.gameOver)
        assertNull(state.question)
    }

    @Test
    fun `next question updates current question`() = runTest {
        val api = FakeTuneGameApi()
        val client = GameClient(api)

        client.newGame()
        val state = client.nextQuestion()

        assertEquals(
            "Who wrote Come Together?",
            state.question?.question
        )

        assertEquals(
            QuestionType.SONG_TO_ARTIST,
            state.question?.type
        )
    }

    @Test
    fun `answer updates score streak and lives`() = runTest {
        val api = FakeTuneGameApi()
        val client = GameClient(api)

        client.newGame()
        client.nextQuestion()

        val state =
            client.answer("The Beatles")

        assertEquals(10, state.score)
        assertEquals(1, state.streak)
        assertEquals(3, state.lives)
        assertFalse(state.gameOver)

        // Once answered, no question should remain active.
        assertNull(state.question)
    }

    @Test
    fun `submit score sends player name and game id`() = runTest {
        val api = FakeTuneGameApi()
        val client = GameClient(api)

        client.newGame()

        // For this test, make the game appear finished by
        // returning a game-over result from a dedicated fake.
        val finishedApi = object : TuneGameApi by api {
            override suspend fun submitAnswer(
                gameId: String,
                answer: String
            ): AnswerResult {
                return AnswerResult(
                    correct = false,
                    correctAnswer = "The Beatles",
                    pointsAwarded = 0,
                    score = 10,
                    streak = 0,
                    lives = 0,
                    gameOver = true
                )
            }

            override suspend fun submitScore(
                playerName: String,
                gameId: String
            ): Score {
                api.submittedPlayerName = playerName
                api.submittedGameId = gameId

                return Score(
                    id = "score-1",
                    playerName = playerName,
                    score = 10
                )
            }
        }

        val finishedClient =
            GameClient(finishedApi)

        finishedClient.newGame()
        finishedClient.nextQuestion()
        finishedClient.answer("Wrong answer")

        assertTrue(
            finishedClient.state.value.gameOver
        )

        finishedClient.submitScore("Marc")

        assertEquals(
            "Marc",
            api.submittedPlayerName
        )

        assertEquals(
            "game-123",
            api.submittedGameId
        )
    }

    @Test
    fun `loading is false after new game completes`() = runTest {
        val api = FakeTuneGameApi()
        val client = GameClient(api)

        client.newGame()

        assertFalse(
            client.state.value.isLoading
        )

        assertEquals(
            null,
            client.state.value.error
        )
    }

    @Test
    fun `question failure updates error state`() = runTest {
        val api = object : TuneGameApi by FakeTuneGameApi() {

            override suspend fun getQuestion(
                gameId: String
            ): QuestionResponse {
                error("Server unavailable")
            }
        }

        val client = GameClient(api)

        client.newGame()
        client.nextQuestion()

        assertFalse(
            client.state.value.isLoading
        )

        assertEquals(
            "Server unavailable",
            client.state.value.error
        )
    }

    @Test
    fun `successful request clears previous error`() = runTest {

        var shouldFail = true

        val api = object : TuneGameApi by FakeTuneGameApi() {

            override suspend fun getQuestion(
                gameId: String
            ): QuestionResponse {

                if (shouldFail) {
                    error("Temporary error")
                }

                return QuestionResponse(
                    PublicQuestion(
                        question = "Who wrote Come Together?",
                        choices = listOf(
                            "The Beatles",
                            "Queen",
                            "Pink Floyd",
                            "The Who"
                        ),
                        type = QuestionType.SONG_TO_ARTIST
                    )
                )
            }
        }

        val client = GameClient(api)

        client.newGame()

        client.nextQuestion()

        assertEquals(
            "Temporary error",
            client.state.value.error
        )

        shouldFail = false

        client.nextQuestion()

        assertEquals(
            null,
            client.state.value.error
        )

        assertEquals(
            "Who wrote Come Together?",
            client.state.value.question?.question
        )
    }
}