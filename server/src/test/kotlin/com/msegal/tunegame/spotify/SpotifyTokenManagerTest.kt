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

        var lastRefreshToken:
                String? =
            null

        override suspend fun exchangeCode(
            code: String
        ): SpotifyToken {

            error(
                "Not needed in these tests"
            )
        }

        override suspend fun refreshToken(
            refreshToken: String
        ): SpotifyToken {

            refreshCalls++

            lastRefreshToken =
                refreshToken

            return refreshedToken
                ?: error(
                    "No refreshed token configured"
                )
        }
    }

    @Test
    fun `returns null when session has no token`() =
        runTest {

            val provider =
                FakeTokenProvider()

            val repository =
                SpotifyTokenRepository(
                    databaseUrl =
                        "jdbc:sqlite::memory:"
                )

            val manager =
                SpotifyTokenManager(
                    tokenService =
                        provider,
                    tokenRepository =
                        repository,
                    clock =
                        { 0L }
                )

            assertNull(
                manager.getAccessToken(
                    "session-1"
                )
            )

            assertEquals(
                0,
                provider.refreshCalls
            )
        }

    @Test
    fun `returns existing access token when token is valid`() =
        runTest {

            var time = 0L

            val provider =
                FakeTokenProvider()

            val repository =
                SpotifyTokenRepository(
                    databaseUrl =
                        "jdbc:sqlite::memory:"
                )

            val manager =
                SpotifyTokenManager(
                    tokenService =
                        provider,
                    tokenRepository =
                        repository,
                    clock =
                        { time }
                )

            manager.setToken(
                sessionId =
                    "session-1",
                token =
                    SpotifyToken(
                        accessToken =
                            "access-1",
                        tokenType =
                            "Bearer",
                        expiresIn =
                            3600,
                        refreshToken =
                            "refresh-1"
                    )
            )

            time = 1_000L

            assertEquals(
                "access-1",
                manager.getAccessToken(
                    "session-1"
                )
            )

            assertEquals(
                0,
                provider.refreshCalls
            )
        }

    @Test
    fun `expired token is refreshed`() =
        runTest {

            var time = 0L

            val provider =
                FakeTokenProvider(
                    refreshedToken =
                        SpotifyToken(
                            accessToken =
                                "access-2",
                            tokenType =
                                "Bearer",
                            expiresIn =
                                3600,
                            refreshToken =
                                "refresh-2"
                        )
                )

            val repository =
                SpotifyTokenRepository(
                    databaseUrl =
                        "jdbc:sqlite::memory:"
                )

            val manager =
                SpotifyTokenManager(
                    tokenService =
                        provider,
                    tokenRepository =
                        repository,
                    clock =
                        { time }
                )

            manager.setToken(
                sessionId =
                    "session-1",
                token =
                    SpotifyToken(
                        accessToken =
                            "access-1",
                        tokenType =
                            "Bearer",
                        expiresIn =
                            3600,
                        refreshToken =
                            "refresh-1"
                    )
            )

            time =
                3_700_000L

            assertEquals(
                "access-2",
                manager.getAccessToken(
                    "session-1"
                )
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
    fun `old refresh token is retained when Spotify does not return a new one`() =
        runTest {

            var time = 0L

            val provider =
                FakeTokenProvider(
                    refreshedToken =
                        SpotifyToken(
                            accessToken =
                                "access-2",
                            tokenType =
                                "Bearer",
                            expiresIn =
                                3600,
                            refreshToken =
                                null
                        )
                )

            val repository =
                SpotifyTokenRepository(
                    databaseUrl =
                        "jdbc:sqlite::memory:"
                )

            val manager =
                SpotifyTokenManager(
                    tokenService =
                        provider,
                    tokenRepository =
                        repository,
                    clock =
                        { time }
                )

            manager.setToken(
                sessionId =
                    "session-1",
                token =
                    SpotifyToken(
                        accessToken =
                            "access-1",
                        tokenType =
                            "Bearer",
                        expiresIn =
                            3600,
                        refreshToken =
                            "refresh-1"
                    )
            )

            time =
                3_700_000L

            assertEquals(
                "access-2",
                manager.getAccessToken(
                    "session-1"
                )
            )

            assertEquals(
                1,
                provider.refreshCalls
            )

            time =
                7_400_000L

            manager.getAccessToken(
                "session-1"
            )

            assertEquals(
                2,
                provider.refreshCalls
            )

            assertEquals(
                "refresh-1",
                provider.lastRefreshToken
            )
        }

    @Test
    fun `tokens are isolated between sessions`() =
        runTest {

            val provider =
                FakeTokenProvider()

            val repository =
                SpotifyTokenRepository(
                    databaseUrl =
                        "jdbc:sqlite::memory:"
                )

            val manager =
                SpotifyTokenManager(
                    tokenService =
                        provider,
                    tokenRepository =
                        repository,
                    clock =
                        { 0L }
                )

            manager.setToken(
                sessionId =
                    "session-a",
                token =
                    SpotifyToken(
                        accessToken =
                            "access-a",
                        tokenType =
                            "Bearer",
                        expiresIn =
                            3600,
                        refreshToken =
                            "refresh-a"
                    )
            )

            manager.setToken(
                sessionId =
                    "session-b",
                token =
                    SpotifyToken(
                        accessToken =
                            "access-b",
                        tokenType =
                            "Bearer",
                        expiresIn =
                            3600,
                        refreshToken =
                            "refresh-b"
                    )
            )

            assertEquals(
                "access-a",
                manager.getAccessToken(
                    "session-a"
                )
            )

            assertEquals(
                "access-b",
                manager.getAccessToken(
                    "session-b"
                )
            )
        }

    @Test
    fun `logout removes token for only specified session`() =
        runTest {

            val provider =
                FakeTokenProvider()

            val repository =
                SpotifyTokenRepository(
                    databaseUrl =
                        "jdbc:sqlite::memory:"
                )

            val manager =
                SpotifyTokenManager(
                    tokenService =
                        provider,
                    tokenRepository =
                        repository,
                    clock =
                        { 0L }
                )

            manager.setToken(
                sessionId =
                    "session-a",
                token =
                    SpotifyToken(
                        accessToken =
                            "access-a",
                        tokenType =
                            "Bearer",
                        expiresIn =
                            3600,
                        refreshToken =
                            "refresh-a"
                    )
            )

            manager.setToken(
                sessionId =
                    "session-b",
                token =
                    SpotifyToken(
                        accessToken =
                            "access-b",
                        tokenType =
                            "Bearer",
                        expiresIn =
                            3600,
                        refreshToken =
                            "refresh-b"
                    )
            )

            manager.logout(
                "session-a"
            )

            assertNull(
                manager.getAccessToken(
                    "session-a"
                )
            )

            assertEquals(
                "access-b",
                manager.getAccessToken(
                    "session-b"
                )
            )
        }
}