package com.msegal.tunegame.spotify

class SpotifyTokenManager(
    private val tokenService: SpotifyTokenProvider,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var token: SpotifyToken? = null
    private var expiresAt: Long = 0L

    fun setToken(newToken: SpotifyToken) {
        token = newToken

        expiresAt =
            clock() +
                    (newToken.expiresIn * 1000L)
    }

    suspend fun getAccessToken(): String? {
        val currentToken = token ?: return null

        val refreshEarlyMs = 60_000L

        if (
            clock() <
            expiresAt - refreshEarlyMs
        ) {
            return currentToken.accessToken
        }

        val refreshToken =
            currentToken.refreshToken
                ?: return null

        val refreshedToken =
            tokenService.refreshToken(refreshToken)

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