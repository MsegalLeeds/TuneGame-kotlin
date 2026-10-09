package com.msegal.tunegame.spotify

import java.util.concurrent.ConcurrentHashMap

class SpotifyTokenManager(
    private val tokenService: SpotifyTokenProvider,
    private val tokenRepository: SpotifyTokenRepository,
    private val clock: () -> Long =
        System::currentTimeMillis
) {

    private data class CachedToken(
        val accessToken: String,
        val expiresAt: Long
    )

    private val accessTokens =
        ConcurrentHashMap<String, CachedToken>()

    suspend fun getAccessToken(
        sessionId: String
    ): String? {

        val cached =
            accessTokens[sessionId]

        if (
            cached != null &&
            clock() < cached.expiresAt
        ) {
            return cached.accessToken
        }

        val refreshToken =
            tokenRepository.getRefreshToken(
                sessionId
            )
                ?: return null

        val token =
            tokenService.refreshToken(
                refreshToken
            )

        storeToken(
            sessionId = sessionId,
            token = token
        )

        return token.accessToken
    }

    suspend fun exchangeCode(
        sessionId: String,
        code: String
    ) {

        val token =
            tokenService.exchangeCode(
                code
            )

        storeToken(
            sessionId = sessionId,
            token = token
        )
    }

    fun setToken(
        sessionId: String,
        token: SpotifyToken
    ) {
        storeToken(
            sessionId = sessionId,
            token = token
        )
    }

    fun logout(
        sessionId: String
    ) {

        accessTokens.remove(
            sessionId
        )

        tokenRepository.deleteRefreshToken(
            sessionId
        )
    }

    private fun storeToken(
        sessionId: String,
        token: SpotifyToken
    ) {

        val expiresAt =
            clock() +
                    token.expiresIn * 1000L -
                    30_000L

        accessTokens[sessionId] =
            CachedToken(
                accessToken =
                    token.accessToken,
                expiresAt =
                    expiresAt
            )

        val refreshToken =
            token.refreshToken

        if (refreshToken != null) {
            tokenRepository.saveRefreshToken(
                sessionId = sessionId,
                refreshToken = refreshToken
            )
        }
    }
}