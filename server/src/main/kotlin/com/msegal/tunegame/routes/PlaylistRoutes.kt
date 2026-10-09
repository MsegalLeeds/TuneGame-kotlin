package com.msegal.tunegame.routes

import com.msegal.tunegame.api.CreatePlaylistRequest
import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.playlist.PlaylistRepository
import com.msegal.tunegame.session.getOrCreateUserSession
import com.msegal.tunegame.spotify.SpotifyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.playlistRoutes(
    playlistRepository: PlaylistRepository,
    spotifyServiceForSession:
        (String) -> SpotifyService
) {

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
            request.spotifyPlaylistId
                .isBlank()
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "name and spotifyPlaylistId are required"
                )
            )

            return@post
        }

        val userSession =
            call.getOrCreateUserSession()

        val spotifyService =
            spotifyServiceForSession(
                userSession.id
            )

        try {

            val spotifyName =
                try {

                    spotifyService
                        .getPlaylistName(
                            request.spotifyPlaylistId
                        )

                } catch (e: Exception) {

                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse(
                            e.message
                                ?: "Could not load Spotify playlist"
                        )
                    )

                    return@post
                }

            val playlist =
                playlistRepository.create(
                    name =
                        spotifyName,
                    spotifyPlaylistId =
                        request.spotifyPlaylistId
                )

            call.respond(
                HttpStatusCode.Created,
                playlist
            )

        } catch (
            e: IllegalStateException
        ) {

            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    e.message
                        ?: "Unable to create playlist"
                )
            )
        }
    }

    delete("/playlist/{id}") {

        val id =
            call.parameters[
                "id"
            ]

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
            playlistRepository.delete(
                id
            )

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
}