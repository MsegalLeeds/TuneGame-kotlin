package com.msegal.tunegame.spotify

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
}