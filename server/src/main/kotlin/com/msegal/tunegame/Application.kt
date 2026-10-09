package com.msegal.tunegame

import com.msegal.tunegame.game.GameSessionManager
import com.msegal.tunegame.game.Song
import com.msegal.tunegame.playlist.PlaylistRepository
import com.msegal.tunegame.repository.SongRepository
import com.msegal.tunegame.routes.gameRoutes
import com.msegal.tunegame.routes.playlistRoutes
import com.msegal.tunegame.routes.scoreRoutes
import com.msegal.tunegame.routes.spotifyRoutes
import com.msegal.tunegame.score.ScoreRepository
import com.msegal.tunegame.spotify.RealSpotifyService
import com.msegal.tunegame.spotify.SpotifyAuth
import com.msegal.tunegame.spotify.SpotifyConfig
import com.msegal.tunegame.spotify.SpotifyService
import com.msegal.tunegame.spotify.SpotifyTokenManager
import com.msegal.tunegame.spotify.SpotifyTokenRepository
import com.msegal.tunegame.spotify.SpotifyTokenService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

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
 * Tests can supply their own songs and optionally
 * provide a fake Spotify service.
 */
fun Application.module(
    songs: List<Song>,
    spotifyConfig: SpotifyConfig? = null,
    spotifyServiceOverride: SpotifyService? = null,
    databaseUrl: String = "jdbc:sqlite:tunegame.db"
) {
    install(ServerContentNegotiation) {
        json()
    }

    install(CORS) {
        anyHost()

        allowHeader(
            HttpHeaders.ContentType
        )

        allowMethod(
            HttpMethod.Delete
        )
    }

    /*
     * Game state
     */
    val sessionManager =
        GameSessionManager(
            songs
        )

    /*
     * Persistent repositories
     */
    val playlistRepository =
        PlaylistRepository(
            databaseUrl = databaseUrl
        )

    val spotifyTokenRepository =
        SpotifyTokenRepository(
            databaseUrl = databaseUrl
        )

    /*
     * Currently still in-memory.
     */
    val scoreRepository =
        ScoreRepository(
            databaseUrl = databaseUrl
        )

    /*
     * Spotify HTTP client.
     *
     * Only configured with Spotify credentials
     * when Spotify configuration is available.
     */
    val spotifyClient: HttpClient? =
        spotifyConfig?.let {
            HttpClient(CIO) {
                install(
                    ClientContentNegotiation
                ) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        }
                    )
                }
            }
        }

    /*
     * Spotify OAuth helper.
     */
    val spotifyAuth: SpotifyAuth? =
        spotifyConfig?.let {
            SpotifyAuth(
                it
            )
        }

    /*
     * Handles authorization-code exchange
     * and refresh-token requests.
     */
    val spotifyTokenService:
            SpotifyTokenService? =
        if (
            spotifyConfig != null &&
            spotifyClient != null
        ) {
            SpotifyTokenService(
                spotifyConfig,
                spotifyClient
            )
        } else {
            null
        }

    /*
     * Manages Spotify access tokens.
     *
     * Refresh tokens are persisted through
     * SpotifyTokenRepository.
     */
    val spotifyTokenManager:
            SpotifyTokenManager? =
        spotifyTokenService?.let {
            SpotifyTokenManager(
                tokenService = it,
                tokenRepository =
                    spotifyTokenRepository
            )
        }

    /*
     * Spotify API implementation.
     *
     * Tests may replace this with
     * FakeSpotifyService.
     */
    val spotifyService: SpotifyService =
        spotifyServiceOverride
            ?: RealSpotifyService(
                client =
                    spotifyClient
                        ?: HttpClient(CIO) {
                            install(
                                ClientContentNegotiation
                            ) {
                                json(
                                    Json {
                                        ignoreUnknownKeys =
                                            true
                                    }
                                )
                            }
                        },
                accessToken = {
                    spotifyTokenManager
                        ?.getAccessToken()
                }
            )

    routing {

        get("/") {
            call.respondText(
                "TuneGame server is running!"
            )
        }

        gameRoutes(
            sessionManager =
                sessionManager,
            spotifyService =
                spotifyService,
            playlistRepository =
                playlistRepository
        )

        playlistRoutes(
            playlistRepository =
                playlistRepository,
            spotifyService =
                spotifyService
        )

        scoreRoutes(
            scoreRepository =
                scoreRepository,
            sessionManager =
                sessionManager
        )

        spotifyRoutes(
            spotifyAuth =
                spotifyAuth,
            spotifyTokenService =
                spotifyTokenService,
            spotifyTokenManager =
                spotifyTokenManager,
            spotifyService =
                spotifyService
        )
    }

    println(
        "TuneGame server running on http://localhost:8080"
    )
}