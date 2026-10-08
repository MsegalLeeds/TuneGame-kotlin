package com.msegal.tunegame.spotify

interface SpotifyService {

    fun searchTrack(
        song: String,
        artist: String
    ): SpotifyTrack?

    fun playTrack(
        trackUri: String,
        positionMs: Int = 30_000
    )

    fun pause()
}