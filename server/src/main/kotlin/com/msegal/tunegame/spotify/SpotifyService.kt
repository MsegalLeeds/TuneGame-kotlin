package com.msegal.tunegame.spotify

import com.msegal.tunegame.game.Song

interface SpotifyService {
    suspend fun searchTrack(
        song: String,
        artist: String
    ): SpotifyTrack?

    suspend fun playTrack(
        trackUri: String,
        positionMs: Int = 30_000
    )

    suspend fun pause()

    suspend fun getPlaylistSongs(
        playlistId: String
    ): List<Song>
}