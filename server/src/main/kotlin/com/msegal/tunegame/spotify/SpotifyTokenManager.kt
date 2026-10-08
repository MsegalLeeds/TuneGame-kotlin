package com.msegal.tunegame.spotify

class SpotifyTokenManager(
    private val tokenService: SpotifyTokenService
) {
    private var token: SpotifyToken? = null
    private var expiresAt: Long = 0L

    fun setToken(newToken: SpotifyToken) {
        token = newToken

        expiresAt =
            System.currentTimeMillis() +
                    (newToken.expiresIn * 1000L)
    }

    suspend fun getAccessToken(): String? {
        val currentToken = token ?: return null

        // Refresh slightly early so the token doesn't expire
        // while we're making a Spotify request.
        val refreshEarlyMs = 60_000L

        if (
            System.currentTimeMillis() <
            expiresAt - refreshEarlyMs
        ) {
            return currentToken.accessToken
        }

        val refreshToken =
            currentToken.refreshToken
                ?: return null

        val refreshedToken =
            tokenService.refreshToken(refreshToken)

        // Spotify may not return a new refresh token.
        // If not, keep the existing one.
        val updatedToken =
            if (refreshedToken.refreshToken == null) {
                refreshedToken.copy(
                    refreshToken = refreshToken
                )
            } else {
                refreshedToken
            }

        setToken(updatedToken)

        return updatedToken.accessToken
    }
}