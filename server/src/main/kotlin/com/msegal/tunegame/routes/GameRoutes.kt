package com.msegal.tunegame.routes

import com.msegal.tunegame.TIME_LIMIT_SECONDS
import com.msegal.tunegame.api.*
import com.msegal.tunegame.game.GameSessionManager
import com.msegal.tunegame.game.QuestionType
import com.msegal.tunegame.playlist.PlaylistRepository
import com.msegal.tunegame.session.getOrCreateUserSession
import com.msegal.tunegame.spotify.SpotifyService
import io.ktor.server.application.log
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Route.gameRoutes(
    sessionManager: GameSessionManager,
    spotifyServiceForSession: (String) -> SpotifyService,
    spotifyConnectedForSession: suspend (String) -> Boolean,
    playlistRepository: PlaylistRepository
) {
    post("/new-game") {
        val body = call.receiveText()
        val request = try {
            if (body.isBlank()) NewGameRequest() else Json.decodeFromString<NewGameRequest>(body)
        } catch (e: SerializationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid game request"))
            return@post
        }
        val owner = call.getOrCreateUserSession().id
        val playlistId = request.playlistId
        val gameId = if (playlistId == null) {
            sessionManager.createGame(ownerSessionId = owner)
        } else {
            val playlist = playlistRepository.get(playlistId, owner)
            if (playlist == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Playlist not found"))
                return@post
            }
            if (!spotifyConnectedForSession(owner)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Spotify account is not connected"))
                return@post
            }
            val songs = try {
                spotifyServiceForSession(owner).getPlaylistSongs(playlist.spotifyPlaylistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Unable to load Spotify playlist"))
                return@post
            }
            if (songs.size < 4) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Playlist needs at least 4 songs"))
                return@post
            }
            sessionManager.createGame(songs, owner)
        }
        call.respond(NewGameResponse(true, "Game created", gameId, 3))
    }

    get("/question") {
        val gameId = call.request.queryParameters["gameId"]
        if (gameId == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("gameId is required"))
            return@get
        }
        val owner = call.getOrCreateUserSession().id
        val session = sessionManager.getGame(gameId, owner)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Game not found"))
            return@get
        }
        val positionMs = call.request.queryParameters["positionMs"]?.toIntOrNull()?.coerceAtLeast(0) ?: 30_000
        // The same lock protects question creation, answers and score submission.
        session.mutex.withLock {
            if (session.engine.state.gameOver) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Game is over"))
                return@withLock
            }
            if (session.currentQuestion != null) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("A question is already active"))
                return@withLock
            }
            val generated = session.engine.generateQuestionWithSong(QuestionType.entries.random())
            var albumArtUrl: String? = null
            try {
                if (spotifyConnectedForSession(owner)) {
                    val spotify = spotifyServiceForSession(owner)
                    val track = spotify.searchTrack(generated.song.song, generated.song.artist)
                    if (track != null) {
                        albumArtUrl = track.albumArtUrl
                        spotify.playTrack(track.uri, positionMs)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Playback is optional. The text quiz remains playable during Spotify outages.
                call.application.log.warn("Spotify playback unavailable")
            }
            // Do not spend the player's answer time waiting for Spotify.
            session.startQuestion(generated.question, generated.song)
            call.respond(QuestionResponse(PublicQuestion(
                question = generated.question.question,
                choices = generated.question.choices,
                type = generated.question.type,
                albumArtUrl = albumArtUrl
            )))
        }
    }

    post("/answer") {
        val request = call.receive<AnswerRequest>()
        if (request.answer.length > 500) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Answer is too long"))
            return@post
        }
        val owner = call.getOrCreateUserSession().id
        val session = sessionManager.getGame(request.gameId, owner)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Game not found"))
            return@post
        }
        session.mutex.withLock {
            val question = session.currentQuestion
            if (question == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("No active question"))
                return@withLock
            }
            val elapsed = session.elapsedSeconds()
            val result = session.engine.checkAnswer(
                if (elapsed >= TIME_LIMIT_SECONDS) "" else request.answer, question, elapsed
            )
            session.clearQuestion()
            try {
                if (spotifyConnectedForSession(owner)) spotifyServiceForSession(owner).pause()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.application.log.warn("Spotify pause unavailable")
            }
            call.respond(result)
        }
    }
}
