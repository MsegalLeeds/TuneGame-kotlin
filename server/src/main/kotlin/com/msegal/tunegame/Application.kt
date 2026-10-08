package com.msegal.tunegame

import kotlinx.serialization.json.Json
import com.msegal.tunegame.api.*
import com.msegal.tunegame.game.*
import com.msegal.tunegame.repository.SongRepository
import com.msegal.tunegame.spotify.*

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.msegal.tunegame.playlist.PlaylistRepository

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

const val TIME_LIMIT_SECONDS = 30.0

/*
 * Production entry point.
 *
 * Loads the real songs and Spotify configuration.
 */
fun Application.module() {
    module(
        songs = SongRepository().loadSongs(),
        spotifyConfig = SpotifyConfig.fromEnvironmentOrNull()
    )
}

/*
 * Configurable application module.
 *
 * Tests can supply their own songs and leave Spotify disabled.
 */
fun Application.module(
    songs: List<Song>,
    spotifyConfig: SpotifyConfig? = null
) {
    install(ServerContentNegotiation) {
        json()
    }

    val sessionManager = GameSessionManager(songs)

    val playlistRepository = PlaylistRepository()

    /*
     * Spotify is optional.
     *
     * If no configuration exists, the rest of TuneGame
     * still works normally.
     */
    val spotifyClient: HttpClient? = spotifyConfig?.let {
        HttpClient(CIO) {
            install(ClientContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
        }
    }

    val spotifyAuth: SpotifyAuth? = spotifyConfig?.let {
        SpotifyAuth(it)
    }

    val spotifyTokenService: SpotifyTokenService? =
        if (spotifyConfig != null && spotifyClient != null) {
            SpotifyTokenService(
                spotifyConfig,
                spotifyClient
            )
        } else {
            null
        }

    val spotifyTokenManager: SpotifyTokenManager? =
        spotifyTokenService?.let {
            SpotifyTokenManager(it)
        }

    val spotifyService = RealSpotifyService(
        client = spotifyClient ?: HttpClient(CIO) {
            install(ClientContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
        },
        accessToken = {
            spotifyTokenManager?.getAccessToken()
        }
    )

    routing {

        /*
         * Health check
         */
        get("/") {
            call.respondText(
                "TuneGame server is running!"
            )
        }

        get("/spotify/search") {

            val song =
                call.request.queryParameters["song"]

            val artist =
                call.request.queryParameters["artist"]

            if (song == null || artist == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "song and artist are required"
                    )
                )
                return@get
            }

            val track =
                spotifyService.searchTrack(
                    song,
                    artist
                )

            if (track == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(
                        "Track not found"
                    )
                )
                return@get
            }

            call.respond(track)
        }

        get("/spotify/play") {
            val song = call.request.queryParameters["song"]
            val artist = call.request.queryParameters["artist"]

            if (song == null || artist == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("song and artist are required")
                )
                return@get
            }

            val track = spotifyService.searchTrack(
                song = song,
                artist = artist
            )

            if (track == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Track not found")
                )
                return@get
            }

            spotifyService.playTrack(
                trackUri = track.uri,
                positionMs = 30_000
            )

            call.respondText(
                "Playing ${track.name} by ${track.artist}"
            )
        }

        get("/playlist") {
            call.respond(
                playlistRepository.getAll()
            )
        }

        post("/playlist") {
            val request =
                call.receive<CreatePlaylistRequest>()

            if (
                request.name.isBlank() ||
                request.spotifyPlaylistId.isBlank()
            ) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "name and spotifyPlaylistId are required"
                    )
                )
                return@post
            }

            try {
                val playlist =
                    playlistRepository.create(
                        name = request.name,
                        spotifyPlaylistId =
                            request.spotifyPlaylistId
                    )

                call.respond(
                    HttpStatusCode.Created,
                    playlist
                )

            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        e.message ?: "Unable to create playlist"
                    )
                )
            }
        }

        delete("/playlist/{id}") {

            val id =
                call.parameters["id"]

            if (id == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "Playlist id is required"
                    )
                )
                return@delete
            }

            val deleted =
                playlistRepository.delete(id)

            if (!deleted) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(
                        "Playlist not found"
                    )
                )
                return@delete
            }

            call.respond(
                HttpStatusCode.NoContent
            )
        }

        /*
         * Start Spotify OAuth login
         */
        get("/spotify/login") {
            val auth = spotifyAuth

            if (auth == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Spotify is not configured")
                )
                return@get
            }

            val (url, _) =
                auth.createAuthorizationRequest()

            call.respondRedirect(url)
        }

        get("/spotify/pause") {
            spotifyService.pause()

            call.respondText("Spotify playback paused")
        }

        /*
         * Spotify OAuth callback
         */
        get("/spotify/callback") {
            val auth = spotifyAuth
            val tokenService = spotifyTokenService

            if (auth == null || tokenService == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Spotify is not configured")
                )
                return@get
            }

            val error =
                call.request.queryParameters["error"]

            if (error != null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "Spotify authorization failed: $error"
                    )
                )
                return@get
            }

            val code =
                call.request.queryParameters["code"]

            val state =
                call.request.queryParameters["state"]

            if (code == null || state == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "Missing authorization code or state"
                    )
                )
                return@get
            }

            if (!auth.validateState(state)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "Invalid Spotify authorization state"
                    )
                )
                return@get
            }

            val token =
                tokenService.exchangeCode(code)

            spotifyTokenManager?.setToken(token)

            call.respondText(
                "Spotify connected successfully. " +
                        "You can close this page."
            )
        }

        /*
         * Create a new TuneGame
         */
        post("/new-game") {
            val gameId =
                sessionManager.createGame()

            val session =
                sessionManager.getGame(gameId)!!

            call.respond(
                NewGameResponse(
                    ok = true,
                    message = "Game created",
                    gameId = gameId,
                    lives = session.engine.state.lives
                )
            )
        }

        /*
         * Generate the next question
         */
        get("/question") {
            val gameId =
                call.request.queryParameters["gameId"]

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
                sessionManager.getGame(gameId)

            if (session == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(
                        "Game not found"
                    )
                )
                return@get
            }

            if (session.engine.state.lives <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "Game is over"
                    )
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

            try {
                val track = spotifyService.searchTrack(
                    song = generated.song.song,
                    artist = generated.song.artist
                )

                if (track != null) {
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

            val publicQuestion = PublicQuestion(
                question = generated.question.question,
                choices = generated.question.choices,
                type = generated.question.type
            )

            call.respond(
                QuestionResponse(publicQuestion)
            )
        }

        /*
         * Submit an answer
         */
        post("/answer") {
            val request =
                call.receive<AnswerRequest>()

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

            val question =
                session.currentQuestion

            if (question == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "No active question"
                    )
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

    println(
        "TuneGame server running on http://localhost:8080"
    )
}