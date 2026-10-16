package com.msegal.tunegame.routes

import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.session.getOrCreateUserSession
import com.msegal.tunegame.spotify.SpotifyAuth
import com.msegal.tunegame.spotify.SpotifyService
import com.msegal.tunegame.spotify.SpotifyTokenManager
import kotlinx.coroutines.CancellationException
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.spotifyRoutes(
    spotifyAuth: SpotifyAuth?,
    spotifyTokenManager: SpotifyTokenManager?,
    spotifyServiceForSession:
        (String) -> SpotifyService,
    frontendUrl: String,
    spotifyConnectedForSession: suspend (String) -> Boolean
) {

    get("/spotify/playlists") {
        val session = call.getOrCreateUserSession()
        try {
            if (!spotifyConnectedForSession(session.id)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Spotify account is not connected"))
                return@get
            }
            call.respond(spotifyServiceForSession(session.id).getUserPlaylists())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Unable to load Spotify playlists"))
        }
    }

    get("/spotify/login") {

        val auth =
            spotifyAuth

        if (auth == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    "Spotify is not configured"
                )
            )
            return@get
        }

        val session =
            call.getOrCreateUserSession()

        val (url, _) =
            auth.createAuthorizationRequest(
                session.id
            )


        call.respondRedirect(url)
    }

    // First leg runs in the WebView, with its existing signed game session.
    get("/spotify/mobile/start") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.response.headers.append("Referrer-Policy", "no-referrer")
        val auth = spotifyAuth
        if (auth == null || spotifyTokenManager == null) {
            call.respondText("Spotify is not configured", status = HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val ticket = auth.createMobileHandoff(call.getOrCreateUserSession().id)
        call.respondRedirect("/spotify/mobile/authorize?ticket=$ticket")
    }

    // The shell opens only this exact same-origin URL in the system browser.
    get("/spotify/mobile/authorize") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.response.headers.append("Referrer-Policy", "no-referrer")
        val ticket = call.request.queryParameters["ticket"]
        val request = ticket?.let {
            spotifyAuth?.beginMobileAuthorization(it, call.getOrCreateUserSession().id)
        }
        if (request == null) {
            call.respondText("Sign-in expired. Return to TuneGame and connect again.", status = HttpStatusCode.BadRequest)
            return@get
        }
        call.respondRedirect(request.first)
    }

    get("/spotify/callback") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.response.headers.append("Referrer-Policy", "no-referrer")
        val auth = spotifyAuth
        val tokenManager = spotifyTokenManager
        if (auth == null || tokenManager == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Spotify is not configured"))
            return@get
        }
        val state = call.request.queryParameters["state"]
        val authorization = state?.let {
            auth.consumeAuthorization(it, call.getOrCreateUserSession().id)
        }
        if (authorization == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid Spotify authorization state"))
            return@get
        }
        if (call.request.queryParameters["error"] != null) {
            call.finishSpotifyAuthorization(authorization.mobile, frontendUrl, connected = false)
            return@get
        }
        val code = call.request.queryParameters["code"]
        if (code == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing authorization code"))
            return@get
        }
        try {
            tokenManager.exchangeCode(sessionId = authorization.sessionId, code = code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.finishSpotifyAuthorization(authorization.mobile, frontendUrl, connected = false)
            return@get
        }
        call.finishSpotifyAuthorization(authorization.mobile, frontendUrl, connected = true)
    }

    get("/spotify/status") {

        val session =
            call.getOrCreateUserSession()

        val connected =
            spotifyTokenManager
                ?.hasStoredAuth(
                    session.id
                )
                ?: false


        call.respond(
            mapOf(
                "connected" to connected
            )
        )
    }

    post("/spotify/logout") {

        val session =
            call.getOrCreateUserSession()

        val tokenManager =
            spotifyTokenManager

        if (tokenManager == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    "Spotify is not configured"
                )
            )
            return@post
        }

        tokenManager.logout(
            session.id
        )

        call.respond(
            mapOf(
                "connected" to false
            )
        )
    }

    get("/spotify/search") {

        val session =
            call.getOrCreateUserSession()

        val song =
            call.request
                .queryParameters["song"]

        val artist =
            call.request
                .queryParameters["artist"]

        if (
            song == null ||
            artist == null
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "song and artist are required"
                )
            )
            return@get
        }

        try {

            val spotifyService = spotifyServiceForSession(session.id)
            val track =
                spotifyService.searchTrack(
                    song = song,
                    artist = artist
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

        } catch (e: Exception) {

            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    e.message
                        ?: "Spotify request failed"
                )
            )
        }
    }

    post("/spotify/play") {

        val session =
            call.getOrCreateUserSession()

        val song =
            call.request
                .queryParameters["song"]

        val artist =
            call.request
                .queryParameters["artist"]

        if (
            song == null ||
            artist == null
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "song and artist are required"
                )
            )
            return@post
        }

        try {

            val spotifyService = spotifyServiceForSession(session.id)
            val track =
                spotifyService.searchTrack(
                    song = song,
                    artist = artist
                )

            if (track == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(
                        "Track not found"
                    )
                )
                return@post
            }

            spotifyService.playTrack(
                trackUri = track.uri,
                positionMs = 30_000
            )

            call.respondText(
                "Playing ${track.name} by ${track.artist}"
            )

        } catch (e: Exception) {

            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    e.message
                        ?: "Spotify playback failed"
                )
            )
        }
    }

    post("/spotify/pause") {

        val session =
            call.getOrCreateUserSession()

        try {

            val spotifyService = spotifyServiceForSession(session.id)
            spotifyService.pause()

            call.respondText(
                "Spotify playback paused"
            )

        } catch (e: Exception) {

            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    e.message
                        ?: "Spotify pause failed"
                )
            )
        }
    }
}
private suspend fun ApplicationCall.finishSpotifyAuthorization(mobile: Boolean, frontendUrl: String, connected: Boolean) {
    if (mobile) {
        val message = if (connected) "Spotify connected." else "Spotify was not connected. Try again in the app."
        respondText("$message Return to TuneGame. On iOS, tap Done; on Android, go back to the app.")
    } else {
        respondRedirect("$frontendUrl/?spotify=${if (connected) "connected" else "error"}")
    }
}
