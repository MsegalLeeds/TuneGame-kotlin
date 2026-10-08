package com.msegal.tunegame.routes

import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.spotify.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.spotifyRoutes(
    spotifyAuth: SpotifyAuth?,
    spotifyTokenService: SpotifyTokenService?,
    spotifyTokenManager: SpotifyTokenManager?,
    spotifyService: SpotifyService
) {

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

        try {
            val track =
                spotifyService.searchTrack(
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

            call.respond(track)

        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    e.message ?: "Spotify request failed"
                )
            )
        }
    }

    get("/spotify/play") {
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

        try {
            val track =
                spotifyService.searchTrack(
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

        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    e.message ?: "Spotify playback failed"
                )
            )
        }
    }

    get("/spotify/pause") {
        try {
            spotifyService.pause()

            call.respondText(
                "Spotify playback paused"
            )

        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    e.message ?: "Spotify pause failed"
                )
            )
        }
    }
}