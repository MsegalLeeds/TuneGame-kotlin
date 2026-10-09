package com.msegal.tunegame.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyTokenRepositoryTest {

    @Test
    fun `refresh token can be saved and loaded`() {

        val repository =
            SpotifyTokenRepository(
                databaseUrl =
                    "jdbc:sqlite::memory:"
            )

        repository.saveRefreshToken(
            sessionId =
                "session-1",
            refreshToken =
                "refresh-token"
        )

        assertEquals(
            "refresh-token",
            repository.getRefreshToken(
                "session-1"
            )
        )
    }

    @Test
    fun `refresh token can be replaced`() {

        val repository =
            SpotifyTokenRepository(
                databaseUrl =
                    "jdbc:sqlite::memory:"
            )

        repository.saveRefreshToken(
            sessionId =
                "session-1",
            refreshToken =
                "old-token"
        )

        repository.saveRefreshToken(
            sessionId =
                "session-1",
            refreshToken =
                "new-token"
        )

        assertEquals(
            "new-token",
            repository.getRefreshToken(
                "session-1"
            )
        )
    }

    @Test
    fun `missing refresh token returns null`() {

        val repository =
            SpotifyTokenRepository(
                databaseUrl =
                    "jdbc:sqlite::memory:"
            )

        assertNull(
            repository.getRefreshToken(
                "missing-session"
            )
        )
    }

    @Test
    fun `refresh token can be deleted`() {

        val repository =
            SpotifyTokenRepository(
                databaseUrl =
                    "jdbc:sqlite::memory:"
            )

        repository.saveRefreshToken(
            sessionId =
                "session-1",
            refreshToken =
                "refresh-token"
        )

        assertTrue(
            repository.deleteRefreshToken(
                "session-1"
            )
        )

        assertNull(
            repository.getRefreshToken(
                "session-1"
            )
        )

        assertFalse(
            repository.deleteRefreshToken(
                "session-1"
            )
        )
    }

    @Test
    fun `refresh tokens are stored separately by session`() {

        val repository =
            SpotifyTokenRepository(
                databaseUrl =
                    "jdbc:sqlite::memory:"
            )

        repository.saveRefreshToken(
            sessionId =
                "session-one",
            refreshToken =
                "token-one"
        )

        repository.saveRefreshToken(
            sessionId =
                "session-two",
            refreshToken =
                "token-two"
        )

        assertEquals(
            "token-one",
            repository.getRefreshToken(
                "session-one"
            )
        )

        assertEquals(
            "token-two",
            repository.getRefreshToken(
                "session-two"
            )
        )
    }

    @Test
    fun `deleting one session token does not delete another`() {

        val repository =
            SpotifyTokenRepository(
                databaseUrl =
                    "jdbc:sqlite::memory:"
            )

        repository.saveRefreshToken(
            sessionId =
                "session-one",
            refreshToken =
                "token-one"
        )

        repository.saveRefreshToken(
            sessionId =
                "session-two",
            refreshToken =
                "token-two"
        )

        repository.deleteRefreshToken(
            "session-one"
        )

        assertNull(
            repository.getRefreshToken(
                "session-one"
            )
        )

        assertEquals(
            "token-two",
            repository.getRefreshToken(
                "session-two"
            )
        )
    }
}