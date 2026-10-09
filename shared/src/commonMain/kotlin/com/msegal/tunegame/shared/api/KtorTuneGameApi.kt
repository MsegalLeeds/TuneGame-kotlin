package com.msegal.tunegame.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class KtorTuneGameApi(
    private val baseUrl: String,
    private val client: HttpClient
) : TuneGameApi {

    private suspend inline fun <reified T> handleResponse(
        response: HttpResponse
    ): T {

        if (!response.status.isSuccess()) {

            val error =
                response.body<ErrorResponse>()

            throw IllegalStateException(
                error.error
            )
        }

        return response.body()
    }

    override suspend fun newGame(
        playlistId: String?
    ): NewGameResponse {

        val response =
            client.post("$baseUrl/new-game") {

                contentType(
                    ContentType.Application.Json
                )

                setBody(
                    NewGameRequest(
                        playlistId = playlistId
                    )
                )
            }

        return handleResponse(response)
    }

    override suspend fun getQuestion(
        gameId: String
    ): QuestionResponse {

        val response =
            client.get(
                "$baseUrl/question"
            ) {
                parameter(
                    "gameId",
                    gameId
                )
            }

        return handleResponse(response)
    }

    override suspend fun submitAnswer(
        gameId: String,
        answer: String
    ): AnswerResult {

        val response =
            client.post(
                "$baseUrl/answer"
            ) {

                contentType(
                    ContentType.Application.Json
                )

                setBody(
                    AnswerRequest(
                        gameId = gameId,
                        answer = answer
                    )
                )
            }

        return handleResponse(response)
    }

    override suspend fun getPlaylists():
            List<Playlist> {

        val response =
            client.get(
                "$baseUrl/playlist"
            )

        return handleResponse(response)
    }

    override suspend fun createPlaylist(
        name: String,
        spotifyPlaylistId: String
    ): Playlist {

        val response =
            client.post(
                "$baseUrl/playlist"
            ) {

                contentType(
                    ContentType.Application.Json
                )

                setBody(
                    CreatePlaylistRequest(
                        name = name,
                        spotifyPlaylistId =
                            spotifyPlaylistId
                    )
                )
            }

        return handleResponse(response)
    }

    override suspend fun deletePlaylist(
        id: String
    ) {

        val response =
            client.delete(
                "$baseUrl/playlist/$id"
            )

        if (!response.status.isSuccess()) {

            val error =
                response.body<ErrorResponse>()

            throw IllegalStateException(
                error.error
            )
        }
    }

    override suspend fun getScores():
            List<Score> {

        val response =
            client.get(
                "$baseUrl/scores"
            )

        return handleResponse(response)
    }

    override suspend fun submitScore(
        playerName: String,
        gameId: String
    ): Score {

        val response =
            client.post(
                "$baseUrl/scores"
            ) {

                contentType(
                    ContentType.Application.Json
                )

                setBody(
                    CreateScoreRequest(
                        playerName =
                            playerName,
                        gameId =
                            gameId
                    )
                )
            }

        return handleResponse(response)
    }

    override suspend fun getSpotifyStatus():
            SpotifyStatusResponse {

        val response =
            client.get(
                "$baseUrl/spotify/status"
            )

        return handleResponse(response)
    }

    override suspend fun disconnectSpotify():
            SpotifyStatusResponse {

        val response =
            client.post(
                "$baseUrl/spotify/logout"
            )

        return handleResponse(response)
    }
}