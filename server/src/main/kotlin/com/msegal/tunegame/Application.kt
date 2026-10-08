package com.msegal.tunegame

import kotlinx.serialization.json.Json
import com.msegal.tunegame.game.*
import com.msegal.tunegame.repository.SongRepository
import com.msegal.tunegame.spotify.*

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.msegal.tunegame.playlist.PlaylistRepository
import com.msegal.tunegame.score.ScoreRepository
import com.msegal.tunegame.routes.playlistRoutes
import com.msegal.tunegame.routes.scoreRoutes
import com.msegal.tunegame.routes.gameRoutes
import com.msegal.tunegame.routes.spotifyRoutes

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
    val scoreRepository = ScoreRepository()

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

        get("/") {
            call.respondText(
                "TuneGame server is running!"
            )
        }

        gameRoutes(
            sessionManager = sessionManager,
            spotifyService = spotifyService
        )

        playlistRoutes(
            playlistRepository = playlistRepository
        )

        scoreRoutes(
            scoreRepository = scoreRepository,
            sessionManager = sessionManager
        )

        spotifyRoutes(
            spotifyAuth = spotifyAuth,
            spotifyTokenService = spotifyTokenService,
            spotifyTokenManager = spotifyTokenManager,
            spotifyService = spotifyService
        )
    }

    println(
        "TuneGame server running on http://localhost:8080"
    )

}