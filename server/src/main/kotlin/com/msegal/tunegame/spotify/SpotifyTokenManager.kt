package com.msegal.tunegame.spotify

class SpotifyTokenManager(
    private val tokenService: SpotifyTokenProvider,
    private val tokenRepository: SpotifyTokenRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private var token: SpotifyToken? = null
    private var expiresAt: Long = 0L

    init {
        restoreSavedRefreshToken()
    }

    private fun restoreSavedRefreshToken() {

        val refreshToken =
            tokenRepository
                ?.getRefreshToken()
                ?: return

        /*
         * We don't persist access tokens because they expire quickly.
         *
         * This deliberately creates an already-expired token.
         * The first call to getAccessToken() will therefore use
         * the persisted refresh token to request a fresh access token.
         */
        token =
            SpotifyToken(
                accessToken = "",
                tokenType = "Bearer",
                expiresIn = 0,
                refreshToken = refreshToken
            )

        expiresAt = 0L
    }

    fun setToken(
        newToken: SpotifyToken
    ) {

        val previousRefreshToken =
            token?.refreshToken
                ?: tokenRepository
                    ?.getRefreshToken()

        val updatedToken =
            if (
                newToken.refreshToken == null &&
                previousRefreshToken != null
            ) {
                newToken.copy(
                    refreshToken =
                        previousRefreshToken
                )
            } else {
                newToken
            }

        token =
            updatedToken

        expiresAt =
            clock() +
                    (
                            updatedToken.expiresIn *
                                    1000L
                            )

        updatedToken.refreshToken?.let {
                refreshToken ->

            tokenRepository
                ?.saveRefreshToken(
                    refreshToken
                )
        }
    }

    suspend fun getAccessToken(): String? {

        val currentToken =
            token ?: return null

        val refreshEarlyMs =
            60_000L

        if (
            currentToken.accessToken.isNotBlank() &&
            clock() <
            expiresAt - refreshEarlyMs
        ) {
            return currentToken.accessToken
        }

        val refreshToken =
            currentToken.refreshToken
                ?: return null

        val refreshedToken =
            tokenService.refreshToken(
                refreshToken
            )

        val updatedToken =
            if (
                refreshedToken.refreshToken == null
            ) {
                refreshedToken.copy(
                    refreshToken =
                        refreshToken
                )
            } else {
                refreshedToken
            }

        setToken(
            updatedToken
        )

        return updatedToken.accessToken
    }
}