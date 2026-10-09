package com.msegal.tunegame.spotify

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotifyAuthTest {

    private fun createAuth(): SpotifyAuth {

        return SpotifyAuth(
            SpotifyConfig(
                clientId =
                    "client-id",
                clientSecret =
                    "client-secret",
                redirectUri =
                    "http://localhost:8080/spotify/callback"
            )
        )
    }

    @Test
    fun `state is valid for session that created it`() {

        val auth =
            createAuth()

        val (_, state) =
            auth.createAuthorizationRequest(
                "session-a"
            )

        assertTrue(
            auth.validateState(
                state =
                    state,
                sessionId =
                    "session-a"
            )
        )
    }

    @Test
    fun `state cannot be used by another session`() {

        val auth =
            createAuth()

        val (_, state) =
            auth.createAuthorizationRequest(
                "session-a"
            )

        assertFalse(
            auth.validateState(
                state =
                    state,
                sessionId =
                    "session-b"
            )
        )
    }

    @Test
    fun `state can only be used once`() {

        val auth =
            createAuth()

        val (_, state) =
            auth.createAuthorizationRequest(
                "session-a"
            )

        assertTrue(
            auth.validateState(
                state =
                    state,
                sessionId =
                    "session-a"
            )
        )

        assertFalse(
            auth.validateState(
                state =
                    state,
                sessionId =
                    "session-a"
            )
        )
    }
}