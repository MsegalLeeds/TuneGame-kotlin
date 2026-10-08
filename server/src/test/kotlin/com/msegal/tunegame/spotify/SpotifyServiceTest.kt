package com.msegal.tunegame.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotifyServiceTest {

    @Test
    fun `track can be searched`() {

        val spotify = FakeSpotifyService()

        val track = spotify.searchTrack(
            song = "Come Together",
            artist = "The Beatles"
        )

        assertEquals(
            "Come Together",
            track?.name
        )

        assertEquals(
            "The Beatles",
            track?.artist
        )
    }

    @Test
    fun `track can be played`() {

        val spotify = FakeSpotifyService()

        spotify.playTrack(
            "spotify:track:test"
        )

        assertEquals(
            "spotify:track:test",
            spotify.lastPlayedUri
        )
    }

    @Test
    fun `playback can be paused`() {

        val spotify = FakeSpotifyService()

        spotify.playTrack(
            "spotify:track:test"
        )

        spotify.pause()

        assertTrue(spotify.paused)
    }
}