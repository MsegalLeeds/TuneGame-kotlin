package com.msegal.tunegame.spotify

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class SpotifyAuth(
    private val config: SpotifyConfig
) {

    private val validStates = mutableSetOf<String>()

    fun createAuthorizationRequest(): Pair<String, String> {

        val state = UUID.randomUUID().toString()

        validStates.add(state)

        val scopes = listOf(
            "user-modify-playback-state",
            "user-read-playback-state"
        ).joinToString(" ")

        val url = buildString {
            append("https://accounts.spotify.com/authorize")
            append("?response_type=code")
            append("&client_id=${encode(config.clientId)}")
            append("&scope=${encode(scopes)}")
            append("&redirect_uri=${encode(config.redirectUri)}")
            append("&state=${encode(state)}")
        }

        return Pair(url, state)
    }

    fun validateState(state: String): Boolean {
        return validStates.remove(state)
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
        )
    }
}