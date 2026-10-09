package com.msegal.tunegame.routes

import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.session.getOrCreateUserSession
import com.msegal.tunegame.spotify.SpotifyAuth
import com.msegal.tunegame.spotify.SpotifyService
import com.msegal.tunegame.spotify.SpotifyTokenManager
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
        (String) -> SpotifyService
) {

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
                .queryParameters[
                "error"
            ]

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
            call.request
                .queryParameters[
                "code"
            ]

        val state =
            call.request
                .queryParameters[
                "state"
            ]

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

        val userSession =
            call.getOrCreateUserSession()

        if (
            !auth.validateState(
                state = state,
                sessionId =
                    userSession.id
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
                sessionId =
                    userSession.id,
                code =
                    code
            )

        } catch (e: Exception) {

            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    e.message
                        ?: "Spotify token exchange failed"
                )
            )

            return@get
        }

        call.respondText(
            "Spotify connected successfully. " +
                    "You can close this page."
        )
    }

    get("/spotify/search") {

        val userSession =
            call.getOrCreateUserSession()

        val spotifyService =
            spotifyServiceForSession(
                userSession.id
            )

        val song =
            call.request
                .queryParameters[
                "song"
            ]

        val artist =
            call.request
                .queryParameters[
                "artist"
            ]

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

            val track =
                spotifyService.searchTrack(
                    song =
                        song,
                    artist =
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

            call.respond(
                track
            )

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

        val userSession =
            call.getOrCreateUserSession()

        val spotifyService =
            spotifyServiceForSession(
                userSession.id
            )

        val song =
            call.request
                .queryParameters[
                "song"
            ]

        val artist =
            call.request
                .queryParameters[
                "artist"
            ]

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

            val track =
                spotifyService.searchTrack(
                    song =
                        song,
                    artist =
                        artist
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
                trackUri =
                    track.uri,
                positionMs =
                    30_000
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

        val userSession =
            call.getOrCreateUserSession()

        val spotifyService =
            spotifyServiceForSession(
                userSession.id
            )

        try {

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

    get("/spotify/status") {

        val userSession =
            call.getOrCreateUserSession()

        val connected =
            spotifyTokenManager
                ?.getAccessToken(
                    userSession.id
                ) != null

        call.respond(
            mapOf(
                "connected" to connected
            )
        )
    }

    post("/spotify/logout") {

        val userSession =
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
            userSession.id
        )

        call.respond(
            mapOf(
                "connected" to false
            )
        )
    }
}