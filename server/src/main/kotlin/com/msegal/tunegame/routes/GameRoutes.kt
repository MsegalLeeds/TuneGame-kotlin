package com.msegal.tunegame.routes

import com.msegal.tunegame.TIME_LIMIT_SECONDS
import com.msegal.tunegame.api.*
import com.msegal.tunegame.game.*
import com.msegal.tunegame.spotify.SpotifyService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.msegal.tunegame.playlist.PlaylistRepository

fun Route.gameRoutes(
    sessionManager: GameSessionManager,
    spotifyService: SpotifyService,
    playlistRepository: PlaylistRepository
) {

    post("/new-game") {

        val request =
            try {
                call.receiveNullable<NewGameRequest>()
            } catch (e: Exception) {
                null
            }

        val playlistId =
            request?.playlistId

        val gameId =
            if (playlistId == null) {

                // Normal built-in song list
                sessionManager.createGame()

            } else {

                val playlist =
                    playlistRepository.get(playlistId)

                if (playlist == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Playlist not found")
                    )
                    return@post
                }

                val songs =
                    try {
                        spotifyService.getPlaylistSongs(
                            playlist.spotifyPlaylistId
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse(
                                e.message
                                    ?: "Unable to load Spotify playlist"
                            )
                        )
                        return@post
                    }

                if (songs.size < 4) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            "Playlist needs at least 4 songs"
                        )
                    )
                    return@post
                }

                sessionManager.createGame(songs)
            }

        val session =
            sessionManager.getGame(gameId)
                ?: error("Created game could not be found")

        call.respond(
            NewGameResponse(
                ok = true,
                message = "Game created",
                gameId = gameId,
                lives = session.engine.state.lives
            )
        )
    }

    get("/question") {
        val gameId =
            call.request.queryParameters["gameId"]

        if (gameId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("gameId is required")
            )
            return@get
        }

        val session =
            sessionManager.getGame(gameId)

        if (session == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Game not found")
            )
            return@get
        }

        if (session.engine.state.gameOver) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Game is over")
            )
            return@get
        }

        val generated =
            session.engine.generateQuestionWithSong(
                QuestionType.entries.random()
            )

        session.startQuestion(
            question = generated.question,
            song = generated.song
        )

        var albumArtUrl: String? = null

        try {
            val track =
                spotifyService.searchTrack(
                    song = generated.song.song,
                    artist = generated.song.artist
                )

            if (track != null) {
                albumArtUrl = track.albumArtUrl

                spotifyService.playTrack(
                    trackUri = track.uri,
                    positionMs = 30_000
                )
            }
        } catch (e: Exception) {
            println(
                "Spotify playback failed: ${e.message}"
            )
        }

        val publicQuestion =
            PublicQuestion(
                question = generated.question.question,
                choices = generated.question.choices,
                type = generated.question.type,
                albumArtUrl = albumArtUrl
            )

        call.respond(
            QuestionResponse(publicQuestion)
        )
    }

    post("/answer") {
        val request =
            call.receive<AnswerRequest>()

        val session =
            sessionManager.getGame(request.gameId)

        if (session == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Game not found")
            )
            return@post
        }

        val question =
            session.currentQuestion

        if (question == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("No active question")
            )
            return@post
        }

        val elapsedSeconds =
            session.elapsedSeconds()

        if (elapsedSeconds > TIME_LIMIT_SECONDS) {
            val result =
                session.engine.checkAnswer(
                    answer = "",
                    question = question,
                    elapsedSeconds = elapsedSeconds
                )

            try {
                spotifyService.pause()
            } catch (e: Exception) {
                println(
                    "Spotify pause failed: ${e.message}"
                )
            }

            session.clearQuestion()

            call.respond(result)
            return@post
        }

        val result =
            session.engine.checkAnswer(
                answer = request.answer,
                question = question,
                elapsedSeconds = elapsedSeconds
            )

        try {
            spotifyService.pause()
        } catch (e: Exception) {
            println(
                "Spotify pause failed: ${e.message}"
            )
        }

        session.clearQuestion()

        call.respond(result)
    }
}