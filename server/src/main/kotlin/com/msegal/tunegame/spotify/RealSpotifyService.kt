package com.msegal.tunegame.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.msegal.tunegame.game.Song
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class RealSpotifyService(
    private val client: HttpClient,
    private val accessToken: suspend () -> String?
) : SpotifyService {

    override suspend fun searchTrack(
        song: String,
        artist: String
    ): SpotifyTrack? {

        val token = accessToken()
            ?: error("Spotify is not connected")

        val response = client.get(
            "https://api.spotify.com/v1/search"
        ) {
            bearerAuth(token)

            parameter(
                "q",
                "track:$song artist:$artist"
            )

            parameter("type", "track")
            parameter("limit", 1)
        }

        if (!response.status.isSuccess()) {
            error(
                "Spotify search failed: ${response.status}"
            )
        }

        val searchResponse =
            response.body<SpotifySearchResponse>()

        val track =
            searchResponse.tracks.items.firstOrNull()
                ?: return null

        return SpotifyTrack(
            id = track.id,
            name = track.name,
            artist = track.artists
                .joinToString(", ") { it.name },
            uri = track.uri,
            albumArtUrl = track.album.images.firstOrNull()?.url
        )
    }

    override suspend fun playTrack(
        trackUri: String,
        positionMs: Int
    ) {
        val token = accessToken()
            ?: error("Spotify is not connected")

        val response = client.put(
            "https://api.spotify.com/v1/me/player/play"
        ) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)

            setBody(
                SpotifyPlaybackRequest(
                    uris = listOf(trackUri),
                    positionMs = positionMs
                )
            )
        }

        if (!response.status.isSuccess()) {
            error(
                "Spotify playback failed: ${response.status}"
            )
        }
    }

    override suspend fun pause() {
        val token = accessToken()
            ?: error("Spotify is not connected")

        val response = client.put(
            "https://api.spotify.com/v1/me/player/pause"
        ) {
            bearerAuth(token)
        }

        if (!response.status.isSuccess()) {
            error(
                "Spotify pause failed: ${response.status}"
            )
        }
    }

    override suspend fun getPlaylistSongs(
        playlistId: String
    ): List<Song> {

        val token =
            accessToken()
                ?: error("Spotify is not connected")

        val songs =
            mutableListOf<Song>()

        var offset = 0
        val limit = 50

        do {
            val response =
                client.get(
                    "https://api.spotify.com/v1/playlists/$playlistId/items"
                ) {
                    bearerAuth(token)

                    parameter(
                        "limit",
                        limit
                    )

                    parameter(
                        "offset",
                        offset
                    )
                }

            if (!response.status.isSuccess()) {
                error(
                    "Spotify playlist request failed: ${response.status}"
                )
            }

            val page =
                response.body<SpotifyPlaylistItemsResponse>()

            page.items
                .mapNotNull { it.item }
                .forEach { track ->

                    val artist =
                        track.artists
                            .joinToString(", ") {
                                it.name
                            }

                    songs += Song(
                        song = track.name,
                        album = track.album.name,
                        artist = artist
                    )
                }

            offset += limit

        } while (page.next != null)

        return songs.distinct()
    }
}