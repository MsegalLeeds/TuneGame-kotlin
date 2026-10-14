package com.msegal.tunegame

import com.msegal.tunegame.room.PrivateRooms
import com.msegal.tunegame.routes.roomRoutes
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
import com.msegal.tunegame.api.ErrorResponse
import io.ktor.server.application.log
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.application.ApplicationStopped
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.seconds
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
    val config = ServerConfig.fromEnvironment()
    embeddedServer(
        Netty,
        port = config.port,
        host = "0.0.0.0",
        module = { module(SongRepository().loadSongs(), SpotifyConfig.fromEnvironmentOrNull(), databaseUrl = config.databaseUrl, config = config) }
    ).start(wait = true)
}

const val TIME_LIMIT_SECONDS = 30.0

fun Application.module() {
    val config = ServerConfig.fromEnvironment()
    module(
        songs =
            SongRepository()
                .loadSongs(),
        spotifyConfig =
            SpotifyConfig
                .fromEnvironmentOrNull(),
        databaseUrl = config.databaseUrl,
        config = config
    )
}

fun Application.module(
    songs: List<Song>,
    spotifyConfig: SpotifyConfig? = null,
    spotifyServiceOverride: SpotifyService? = null,
    databaseUrl: String = "jdbc:sqlite:tunegame.db",
    playlistRepositoryOverride: PlaylistRepository? = null,
    config: ServerConfig = ServerConfig()
) {

    install(ServerContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.application.log.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Unable to complete request"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ErrorResponse("Too many requests. Please try again shortly."))
        }
    }

    install(RateLimit) {
        global {
            rateLimiter(limit = 300, refillPeriod = 60.seconds)
            // Use the socket peer, never an untrusted forwarding header.
            requestKey { call -> call.request.local.remoteHost }
        }
    }

    install(Sessions) {
        cookie<UserSession>(
            "tunegame_session"
        ) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = config.secureCookies
            transform(SessionTransportTransformerMessageAuthentication(config.sessionSigningKey))

            cookie.extensions[
                "SameSite"
            ] = "Lax"
        }
    }

    if (!config.secureCookies) {
        install(CORS) {
            allowHost("localhost:8081", schemes = listOf("http"))
            allowHost("127.0.0.1:8081", schemes = listOf("http"))
            allowCredentials = true
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
        }
    }

    val sessionManager =
        GameSessionManager(
            songs
        )

    val playlistRepository =
        playlistRepositoryOverride
            ?: PlaylistRepository(
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
                install(HttpTimeout) {
                    requestTimeoutMillis = 8_000
                    connectTimeoutMillis = 3_000
                    socketTimeoutMillis = 8_000
                }

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

    val spotifyConnectedForSession:
            suspend (String) -> Boolean =
        { sessionId ->

            if (spotifyServiceOverride != null) {
                true
            } else {
                spotifyTokenManager
                    ?.getAccessToken(
                        sessionId
                    ) != null
            }
        }

    monitor.subscribe(ApplicationStopped) {
        spotifyClient?.close()
        spotifyTokenRepository.close()
        scoreRepository.close()
        if (playlistRepositoryOverride == null) playlistRepository.close()
    }

    val privateRooms = PrivateRooms(songs)
    routing {
        roomRoutes(privateRooms)
        get("/rooms") { call.respondRedirect("/rooms/") }
        staticResources("/rooms", "web")
        get("/play") { call.respondRedirect("/play/") }
        staticResources("/play", "web")


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
            spotifyConnectedForSession =
                spotifyConnectedForSession,
            playlistRepository =
                playlistRepository
        )

        playlistRoutes(
            playlistRepository =
                playlistRepository,
            spotifyServiceForSession =
                spotifyServiceForSession,
            spotifyConnectedForSession =
                spotifyConnectedForSession
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
                spotifyServiceForSession,
            frontendUrl = "${config.publicUrl}/play",
            spotifyConnectedForSession = spotifyConnectedForSession
        )
    }

    println(
        "TuneGame browser available at ${config.publicUrl}/play/"
    )
}