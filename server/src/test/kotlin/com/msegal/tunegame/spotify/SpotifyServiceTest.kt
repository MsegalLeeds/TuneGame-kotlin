package com.msegal.tunegame.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SpotifyServiceTest {

    @Test
    fun `track can be searched`() = runTest {

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
    fun `track can be played`() = runTest {

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
    fun `playback can be paused`() = runTest {

        val spotify = FakeSpotifyService()

        spotify.playTrack(
            "spotify:track:test"
        )

        spotify.pause()

        assertTrue(spotify.paused)
    }
}