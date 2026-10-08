package com.msegal.tunegame.spotify

class FakeSpotifyService : SpotifyService {

    var lastPlayedUri: String? = null
    var paused = false

    override suspend fun searchTrack(
        song: String,
        artist: String
    ): SpotifyTrack? {

        return SpotifyTrack(
            id = "test-id",
            name = song,
            artist = artist,
            uri = "spotify:track:test"
        )
    }

    override suspend fun playTrack(
        trackUri: String,
        positionMs: Int
    ) {
        lastPlayedUri = trackUri
        paused = false
    }

    override suspend fun pause() {
        paused = true
    }
}