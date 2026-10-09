package com.msegal.tunegame.routes

import com.msegal.tunegame.api.CreatePlaylistRequest
import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.playlist.PlaylistRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.msegal.tunegame.spotify.SpotifyService

fun Route.playlistRoutes(
    playlistRepository: PlaylistRepository,
    spotifyService: SpotifyService
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
            val spotifyName =
                try {
                    spotifyService.getPlaylistName(
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
                    name = spotifyName,
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
                    e.message
                        ?: "Unable to create playlist"
                )
            )
        }
    }

    delete("/playlist/{id}") {
        val id = call.parameters["id"]

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
}