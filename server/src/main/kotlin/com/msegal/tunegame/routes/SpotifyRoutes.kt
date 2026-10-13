package com.msegal.tunegame.routes

import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.session.getOrCreateUserSession
import com.msegal.tunegame.spotify.SpotifyAuth
import com.msegal.tunegame.spotify.SpotifyService
import com.msegal.tunegame.spotify.SpotifyTokenManager
import kotlinx.coroutines.CancellationException
import io.ktor.http.HttpStatusCode
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

    get("/spotify/callback") {

        val auth =
            spotifyAuth

        val tokenManager =
            spotifyTokenManager

        if (
            auth == null ||
            tokenManager == null
        ) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    "Spotify is not configured"
                )
            )
            return@get
        }

        val error =
            call.request
                .queryParameters["error"]

        if (error != null) {
            call.respondRedirect(
                "$frontendUrl/?spotify=error"
            )
            return@get
        }

        val code =
            call.request
                .queryParameters["code"]

        val state =
            call.request
                .queryParameters["state"]

        if (
            code == null ||
            state == null
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "Missing authorization code or state"
                )
            )
            return@get
        }

        val session =
            call.getOrCreateUserSession()


        if (
            !auth.validateState(
                state = state,
                sessionId = session.id
            )
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "Invalid Spotify authorization state"
                )
            )
            return@get
        }

        try {
            tokenManager.exchangeCode(
                sessionId = session.id,
                code = code
            )
        } catch (e: Exception) {

            call.respondRedirect(
                "$frontendUrl/?spotify=error"
            )
            return@get
        }

        call.respondRedirect(
            "$frontendUrl/?spotify=connected"
        )
    }

    get("/spotify/status") {

        val session =
            call.getOrCreateUserSession()

        val connected =
            try {
                spotifyConnectedForSession(session.id)
            } catch (e: Exception) {
                false
            }

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