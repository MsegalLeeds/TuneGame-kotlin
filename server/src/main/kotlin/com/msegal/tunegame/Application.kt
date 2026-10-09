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
import com.msegal.tunegame.session.UserSession
import com.msegal.tunegame.session.getOrCreateUserSession
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
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
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

fun Application.module() {
    module(
        songs =
            SongRepository()
                .loadSongs(),
        spotifyConfig =
            SpotifyConfig
                .fromEnvironmentOrNull()
    )
}

fun Application.module(
    songs: List<Song>,
    spotifyConfig: SpotifyConfig? = null,
    spotifyServiceOverride: SpotifyService? = null,
    databaseUrl: String = "jdbc:sqlite:tunegame.db"
) {

    install(ServerContentNegotiation) {
        json()
    }

    install(Sessions) {
        cookie<UserSession>(
            "tunegame_session"
        ) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions[
                "SameSite"
            ] = "lax"
        }
    }

    install(CORS) {

        allowHost(
            "localhost:8081",
            schemes =
                listOf("http")
        )

        allowHost(
            "127.0.0.1:8081",
            schemes =
                listOf("http")
        )

        allowCredentials = true

        allowHeader(
            HttpHeaders.ContentType
        )

        allowMethod(
            HttpMethod.Post
        )

        allowMethod(
            HttpMethod.Delete
        )
    }

    val sessionManager =
        GameSessionManager(
            songs
        )

    val playlistRepository =
        PlaylistRepository(
            databaseUrl =
                databaseUrl
        )

    val spotifyTokenRepository =
        SpotifyTokenRepository(
            databaseUrl =
                databaseUrl
        )

    val scoreRepository =
        ScoreRepository(
            databaseUrl =
                databaseUrl
        )

    val spotifyClient: HttpClient? =
        spotifyConfig?.let {

            HttpClient(CIO) {

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
            }
        }

    val spotifyAuth =
        spotifyConfig?.let {
            SpotifyAuth(it)
        }

    val spotifyTokenService =
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

    val spotifyTokenManager =
        spotifyTokenService?.let {

            SpotifyTokenManager(
                tokenService =
                    it,
                tokenRepository =
                    spotifyTokenRepository
            )
        }

    val spotifyServiceForSession:
                (String) -> SpotifyService =
        { sessionId ->

            spotifyServiceOverride
                ?: RealSpotifyService(
                    client =
                        spotifyClient
                            ?: error(
                                "Spotify client not configured"
                            ),
                    accessToken = {
                        spotifyTokenManager
                            ?.getAccessToken(
                                sessionId
                            )
                    }
                )
        }

    routing {

        get("/") {
            call.respondText(
                "TuneGame server is running!"
            )
        }

        get("/session") {

            val session =
                call.getOrCreateUserSession()

            call.respond(
                mapOf(
                    "sessionId" to
                            session.id
                )
            )
        }

        gameRoutes(
            sessionManager =
                sessionManager,
            spotifyServiceForSession =
                spotifyServiceForSession,
            playlistRepository =
                playlistRepository
        )

        playlistRoutes(
            playlistRepository =
                playlistRepository,
            spotifyServiceForSession =
                spotifyServiceForSession
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
            spotifyTokenManager =
                spotifyTokenManager,
            spotifyServiceForSession =
                spotifyServiceForSession
        )
    }

    println(
        "TuneGame server running on http://localhost:8080"
    )
}