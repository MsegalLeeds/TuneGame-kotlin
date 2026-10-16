package com.msegal.tunegame.spotify

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpotifyAuth(
    private val config: SpotifyConfig,
    private val clock: () -> Long = System::currentTimeMillis
) {

    data class Authorization(val sessionId: String, val mobile: Boolean)
    private data class PendingState(
        val browserSessionId: String,
        val authorization: Authorization,
        val expiresAt: Long
    )
    private data class Handoff(val sessionId: String, val expiresAt: Long)
    private val validStates = ConcurrentHashMap<String, PendingState>()
    private val handoffs = ConcurrentHashMap<String, Handoff>()

    // The ticket carries no game cookie or Spotify token into the system browser.
    fun createMobileHandoff(sessionId: String): String {
        require(sessionId.isNotBlank())
        discardExpired()
        val ticket = UUID.randomUUID().toString()
        handoffs[ticket] = Handoff(sessionId, clock() + 5 * 60_000)
        return ticket
    }

    fun beginMobileAuthorization(ticket: String, browserSessionId: String): Pair<String, String>? {
        val handoff = handoffs.remove(ticket) ?: return null
        if (clock() >= handoff.expiresAt) return null
        return createRequest(browserSessionId, Authorization(handoff.sessionId, mobile = true))
    }

    private fun discardExpired() {
        val now = clock()
        handoffs.entries.removeIf { it.value.expiresAt <= now }
        validStates.entries.removeIf { it.value.expiresAt <= now }
    }

    fun createAuthorizationRequest(
        sessionId: String
    ): Pair<String, String> = createRequest(sessionId, Authorization(sessionId, mobile = false))

    private fun createRequest(sessionId: String, authorization: Authorization): Pair<String, String> {
        discardExpired()
        require(sessionId.isNotBlank()) {
            "Session ID cannot be blank"
        }

        val state =
            UUID.randomUUID()
                .toString()

        validStates[state] = PendingState(sessionId, authorization, clock() + 10 * 60_000)

        val scopes =
            listOf(
                "user-modify-playback-state",
                "user-read-playback-state",
                "playlist-read-private",
                "playlist-read-collaborative"
            ).joinToString(" ")

        val url =
            buildString {

                append(
                    "https://accounts.spotify.com/authorize"
                )

                append(
                    "?response_type=code"
                )

                append(
                    "&client_id=${
                        encode(
                            config.clientId
                        )
                    }"
                )

                append(
                    "&scope=${
                        encode(scopes)
                    }"
                )

                append(
                    "&redirect_uri=${
                        encode(
                            config.redirectUri
                        )
                    }"
                )

                append(
                    "&state=${
                        encode(state)
                    }"
                )
            }

        return Pair(
            url,
            state
        )
    }

    fun consumeAuthorization(state: String, sessionId: String): Authorization? {
        val pending = validStates[state] ?: return null
        if (clock() >= pending.expiresAt) {
            validStates.remove(state, pending)
            return null
        }
        if (pending.browserSessionId != sessionId || !validStates.remove(state, pending)) return null
        return pending.authorization
    }

    fun validateState(state: String, sessionId: String): Boolean =
        consumeAuthorization(state, sessionId) != null

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
        )
    }
}