package com.msegal.tunegame.spotify

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import java.util.Base64

class SpotifyTokenService(
    private val config: SpotifyConfig,
    private val client: HttpClient
) {

    suspend fun exchangeCode(code: String): SpotifyToken {

        val credentials =
            "${config.clientId}:${config.clientSecret}"

        val encodedCredentials = Base64
            .getEncoder()
            .encodeToString(credentials.toByteArray())

        val response = client.submitForm(
            url = "https://accounts.spotify.com/api/token",
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", config.redirectUri)
            }
        ) {
            header(
                HttpHeaders.Authorization,
                "Basic $encodedCredentials"
            )
        }

        if (!response.status.isSuccess()) {
            error(
                "Spotify token request failed: ${response.status}"
            )
        }

        return response.body()
    }

    suspend fun refreshToken(
        refreshToken: String
    ): SpotifyToken {

        val credentials =
            "${config.clientId}:${config.clientSecret}"

        val encodedCredentials = Base64
            .getEncoder()
            .encodeToString(credentials.toByteArray())

        val response = client.submitForm(
            url = "https://accounts.spotify.com/api/token",
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            }
        ) {
            header(
                HttpHeaders.Authorization,
                "Basic $encodedCredentials"
            )
        }

        if (!response.status.isSuccess()) {
            error(
                "Spotify token refresh failed: ${response.status}"
            )
        }

        return response.body()
    }
}