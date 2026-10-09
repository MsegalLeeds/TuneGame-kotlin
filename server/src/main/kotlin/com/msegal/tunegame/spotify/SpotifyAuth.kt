package com.msegal.tunegame.spotify

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpotifyAuth(
    private val config: SpotifyConfig
) {

    private val validStates =
        ConcurrentHashMap<String, String>()

    fun createAuthorizationRequest(
        sessionId: String
    ): Pair<String, String> {

        require(sessionId.isNotBlank()) {
            "Session ID cannot be blank"
        }

        val state =
            UUID.randomUUID()
                .toString()

        validStates[state] =
            sessionId

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

    fun validateState(
        state: String,
        sessionId: String
    ): Boolean {
        return validStates.remove(
            state,
            sessionId
        )
    }

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
        )
    }
}