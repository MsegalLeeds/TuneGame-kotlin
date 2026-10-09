package com.msegal.tunegame.routes

import com.msegal.tunegame.TIME_LIMIT_SECONDS
import com.msegal.tunegame.api.AnswerRequest
import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.api.NewGameRequest
import com.msegal.tunegame.api.NewGameResponse
import com.msegal.tunegame.api.PublicQuestion
import com.msegal.tunegame.api.QuestionResponse
import com.msegal.tunegame.game.GameSessionManager
import com.msegal.tunegame.game.QuestionType
import com.msegal.tunegame.playlist.PlaylistRepository
import com.msegal.tunegame.session.getOrCreateUserSession
import com.msegal.tunegame.spotify.SpotifyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.gameRoutes(
    sessionManager: GameSessionManager,
    spotifyServiceForSession:
        (String) -> SpotifyService,
    playlistRepository: PlaylistRepository
) {

    post("/new-game") {

        val request =
            try {
                call.receiveNullable<
                        NewGameRequest
                        >()
            } catch (e: Exception) {
                null
            }

        val playlistId =
            request?.playlistId

        val gameId =
            if (playlistId == null) {

                sessionManager.createGame()

            } else {

                val playlist =
                    playlistRepository.get(
                        playlistId
                    )

                if (playlist == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(
                            "Playlist not found"
                        )
                    )

                    return@post
                }

                val userSession =
                    call.getOrCreateUserSession()

                val spotifyService =
                    spotifyServiceForSession(
                        userSession.id
                    )

                val songs =
                    try {

                        spotifyService
                            .getPlaylistSongs(
                                playlist
                                    .spotifyPlaylistId
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

                sessionManager.createGame(
                    songs
                )
            }

        val session =
            sessionManager.getGame(
                gameId
            )
                ?: error(
                    "Created game could not be found"
                )

        call.respond(
            NewGameResponse(
                ok = true,
                message =
                    "Game created",
                gameId =
                    gameId,
                lives =
                    session.engine
                        .state
                        .lives
            )
        )
    }

    get("/question") {

        val gameId =
            call.request
                .queryParameters[
                "gameId"
            ]

        if (gameId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "gameId is required"
                )
            )

            return@get
        }

        val session =
            sessionManager.getGame(
                gameId
            )

        if (session == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    "Game not found"
                )
            )

            return@get
        }

        if (
            session.engine
                .state
                .gameOver
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "Game is over"
                )
            )

            return@get
        }

        if (
            session.currentQuestion !=
            null
        ) {
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    "A question is already active"
                )
            )

            return@get
        }

        val generated =
            session.engine
                .generateQuestionWithSong(
                    QuestionType
                        .entries
                        .random()
                )

        session.startQuestion(
            question =
                generated.question,
            song =
                generated.song
        )

        val userSession =
            call.getOrCreateUserSession()

        val spotifyService =
            spotifyServiceForSession(
                userSession.id
            )

        var albumArtUrl:
                String? =
            null

        try {

            val track =
                spotifyService.searchTrack(
                    song =
                        generated.song.song,
                    artist =
                        generated.song.artist
                )

            if (track != null) {

                albumArtUrl =
                    track.albumArtUrl

                spotifyService.playTrack(
                    trackUri =
                        track.uri,
                    positionMs =
                        30_000
                )
            }

        } catch (e: Exception) {

            println(
                "Spotify playback failed: ${e.message}"
            )
        }

        val publicQuestion =
            PublicQuestion(
                question =
                    generated
                        .question
                        .question,
                choices =
                    generated
                        .question
                        .choices,
                type =
                    generated
                        .question
                        .type,
                albumArtUrl =
                    albumArtUrl
            )

        call.respond(
            QuestionResponse(
                publicQuestion
            )
        )
    }

    post("/answer") {

        val request =
            call.receive<
                    AnswerRequest
                    >()

        val session =
            sessionManager.getGame(
                request.gameId
            )

        if (session == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    "Game not found"
                )
            )

            return@post
        }

        val result =
            synchronized(session) {

                val question =
                    session.currentQuestion
                        ?: return@synchronized null

                val elapsedSeconds =
                    session.elapsedSeconds()

                val answer =
                    if (
                        elapsedSeconds >
                        TIME_LIMIT_SECONDS
                    ) {
                        ""
                    } else {
                        request.answer
                    }

                val answerResult =
                    session.engine
                        .checkAnswer(
                            answer =
                                answer,
                            question =
                                question,
                            elapsedSeconds =
                                elapsedSeconds
                        )

                session.clearQuestion()

                answerResult
            }

        if (result == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "No active question"
                )
            )

            return@post
        }

        val userSession =
            call.getOrCreateUserSession()

        val spotifyService =
            spotifyServiceForSession(
                userSession.id
            )

        try {

            spotifyService.pause()

        } catch (e: Exception) {

            println(
                "Spotify pause failed: ${e.message}"
            )
        }

        call.respond(
            result
        )
    }
}