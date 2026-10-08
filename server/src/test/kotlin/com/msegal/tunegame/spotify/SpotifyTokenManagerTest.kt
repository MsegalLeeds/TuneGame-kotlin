package com.msegal.tunegame.spotify

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyTokenManagerTest {

    private class FakeTokenProvider(
        var refreshedToken: SpotifyToken? = null
    ) : SpotifyTokenProvider {

        var refreshCalls = 0
        var lastRefreshToken: String? = null

        override suspend fun exchangeCode(
            code: String
        ): SpotifyToken {
            error("Not needed in these tests")
        }

        override suspend fun refreshToken(
            refreshToken: String
        ): SpotifyToken {
            refreshCalls++
            lastRefreshToken = refreshToken

            return refreshedToken
                ?: error("No refreshed token configured")
        }
    }

    @Test
    fun `returns null when no token has been set`() = runTest {
        val provider = FakeTokenProvider()

        val manager = SpotifyTokenManager(
            tokenService = provider,
            clock = { 0L }
        )

        assertNull(manager.getAccessToken())
        assertEquals(0, provider.refreshCalls)
    }

    @Test
    fun `returns existing access token when token is valid`() = runTest {
        var time = 0L

        val provider = FakeTokenProvider()

        val manager = SpotifyTokenManager(
            tokenService = provider,
            clock = { time }
        )

        manager.setToken(
            SpotifyToken(
                accessToken = "access-1",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = "refresh-1"
            )
        )

        time = 1_000L

        assertEquals(
            "access-1",
            manager.getAccessToken()
        )

        assertEquals(
            0,
            provider.refreshCalls
        )
    }

    @Test
    fun `expired token is refreshed`() = runTest {
        var time = 0L

        val provider = FakeTokenProvider(
            refreshedToken = SpotifyToken(
                accessToken = "access-2",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = "refresh-2"
            )
        )

        val manager = SpotifyTokenManager(
            tokenService = provider,
            clock = { time }
        )

        manager.setToken(
            SpotifyToken(
                accessToken = "access-1",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = "refresh-1"
            )
        )

        // Move beyond the original token expiry.
        time = 3_700_000L

        assertEquals(
            "access-2",
            manager.getAccessToken()
        )

        assertEquals(
            1,
            provider.refreshCalls
        )

        assertEquals(
            "refresh-1",
            provider.lastRefreshToken
        )
    }

    @Test
    fun `old refresh token is retained when Spotify does not return a new one`() = runTest {
        var time = 0L

        val provider = FakeTokenProvider(
            refreshedToken = SpotifyToken(
                accessToken = "access-2",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = null
            )
        )

        val manager = SpotifyTokenManager(
            tokenService = provider,
            clock = { time }
        )

        manager.setToken(
            SpotifyToken(
                accessToken = "access-1",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = "refresh-1"
            )
        )

        // Expire the first access token.
        time = 3_700_000L

        assertEquals(
            "access-2",
            manager.getAccessToken()
        )

        assertEquals(
            1,
            provider.refreshCalls
        )

        // Expire the refreshed access token too.
        time = 7_400_000L

        manager.getAccessToken()

        assertEquals(
            2,
            provider.refreshCalls
        )

        // This proves the original refresh token was retained.
        assertEquals(
            "refresh-1",
            provider.lastRefreshToken
        )
    }
}