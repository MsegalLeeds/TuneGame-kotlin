package com.msegal.tunegame.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameSessionManagerTest {

    private val songs =
        listOf(
            Song(
                song = "Song 1",
                artist = "Artist 1",
                album = "Album 1"
            )
        )

    @Test
    fun `expired games are removed`() {

        var now = 0L

        val manager =
            GameSessionManager(
                songs = songs,
                clock = { now },
                sessionTtlMs = 1_000L
            )

        val gameId =
            manager.createGame()

        assertNotNull(
            manager.getGame(gameId)
        )

        assertEquals(
            1,
            manager.sessionCount()
        )

        now = 1_001L

        assertNull(
            manager.getGame(gameId)
        )

        assertEquals(
            0,
            manager.sessionCount()
        )
    }

    @Test
    fun `game remains before expiry`() {

        var now = 0L

        val manager =
            GameSessionManager(
                songs = songs,
                clock = { now },
                sessionTtlMs = 1_000L
            )

        val gameId =
            manager.createGame()

        now = 999L

        assertNotNull(
            manager.getGame(gameId)
        )
    }
}