package com.msegal.tunegame.playlist

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlaylistRepository {

    companion object {
        const val MAX_STORED_PLAYLISTS = 50
    }

    private val playlists =
        ConcurrentHashMap<String, Playlist>()

    fun getAll(): List<Playlist> {
        return playlists.values.toList()
    }

    fun get(id: String): Playlist? {
        return playlists[id]
    }

    fun create(
        name: String,
        spotifyPlaylistId: String
    ): Playlist {

        if (playlists.size >= MAX_STORED_PLAYLISTS) {
            error("Maximum number of playlists reached")
        }

        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            spotifyPlaylistId = spotifyPlaylistId
        )

        playlists[playlist.id] = playlist

        return playlist
    }

    fun delete(id: String): Boolean {
        return playlists.remove(id) != null
    }
}