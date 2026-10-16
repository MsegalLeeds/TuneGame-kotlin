package com.msegal.tunegame.spotify

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
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
                    "http://127.0.0.1:8080/spotify/callback"
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
                state = state,
                sessionId = "session-a"
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
                state = state,
                sessionId = "session-b"
            )
        )
    }

    @Test
    fun `wrong session does not consume valid state`() {

        val auth =
            createAuth()

        val (_, state) =
            auth.createAuthorizationRequest(
                "session-a"
            )

        assertFalse(
            auth.validateState(
                state = state,
                sessionId = "session-b"
            )
        )

        assertTrue(
            auth.validateState(
                state = state,
                sessionId = "session-a"
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
                state = state,
                sessionId = "session-a"
            )
        )

        assertFalse(
            auth.validateState(
                state = state,
                sessionId = "session-a"
            )
        )
    }

    @Test
    fun `different sessions receive independent states`() {

        val auth =
            createAuth()

        val (_, stateA) =
            auth.createAuthorizationRequest(
                "session-a"
            )

        val (_, stateB) =
            auth.createAuthorizationRequest(
                "session-b"
            )

        assertTrue(
            auth.validateState(
                state = stateA,
                sessionId = "session-a"
            )
        )

        assertTrue(
            auth.validateState(
                state = stateB,
                sessionId = "session-b"
            )
        )
    }

    @Test
    fun `random unknown state is rejected`() {

        val auth =
            createAuth()

        assertFalse(
            auth.validateState(
                state = "not-a-real-state",
                sessionId = "session-a"
            )
        )
    }

    @Test
    fun `mobile ticket expires after five minutes`() {
        var now = 0L
        val auth = SpotifyAuth(SpotifyConfig("client", "secret", "https://game.example/spotify/callback"), { now })
        val ticket = auth.createMobileHandoff("mobile")
        now = 5 * 60_000L
        assertNull(auth.beginMobileAuthorization(ticket, "browser"))
    }

    @Test
    fun `mobile callback expires after ten minutes and never connects the browser instead`() {
        var now = 0L
        val auth = SpotifyAuth(SpotifyConfig("client", "secret", "https://game.example/spotify/callback"), { now })
        val ticket = auth.createMobileHandoff("mobile")
        val (_, state) = assertNotNull(auth.beginMobileAuthorization(ticket, "browser"))
        now = 10 * 60_000L
        assertNull(auth.consumeAuthorization(state, "browser"))
    }

    @Test
    fun `mobile tickets are single use and callbacks are bound to the browser that consumed them`() {
        val auth = createAuth()
        val ticket = auth.createMobileHandoff("mobile")
        val (_, state) = assertNotNull(auth.beginMobileAuthorization(ticket, "browser"))
        assertNull(auth.beginMobileAuthorization(ticket, "another-browser"))
        assertNull(auth.beginMobileAuthorization("unknown", "browser"))
        assertNull(auth.consumeAuthorization(state, "mobile"))
        assertNull(auth.consumeAuthorization(state, "another-browser"))
        assertEquals(SpotifyAuth.Authorization("mobile", mobile = true), auth.consumeAuthorization(state, "browser"))
        assertNull(auth.consumeAuthorization(state, "browser"))
    }
}