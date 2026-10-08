package com.msegal.tunegame.playlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaylistRepositoryTest {

    @Test
    fun `created playlist can be retrieved`() {
        val repository = PlaylistRepository()

        val playlist = repository.create(
            name = "Classic Rock",
            spotifyPlaylistId = "spotify123"
        )

        val stored = repository.get(playlist.id)

        assertNotNull(stored)
        assertEquals("Classic Rock", stored.name)
        assertEquals(
            "spotify123",
            stored.spotifyPlaylistId
        )
    }

    @Test
    fun `get all returns created playlists`() {
        val repository = PlaylistRepository()

        repository.create(
            name = "Playlist One",
            spotifyPlaylistId = "spotify1"
        )

        repository.create(
            name = "Playlist Two",
            spotifyPlaylistId = "spotify2"
        )

        val playlists = repository.getAll()

        assertEquals(2, playlists.size)
    }

    @Test
    fun `playlist can be deleted`() {
        val repository = PlaylistRepository()

        val playlist = repository.create(
            name = "Delete Me",
            spotifyPlaylistId = "spotify123"
        )

        assertTrue(
            repository.delete(playlist.id)
        )

        assertEquals(
            null,
            repository.get(playlist.id)
        )
    }

    @Test
    fun `deleting unknown playlist returns false`() {
        val repository = PlaylistRepository()

        assertFalse(
            repository.delete("does-not-exist")
        )
    }
}