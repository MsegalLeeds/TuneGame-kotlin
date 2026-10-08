package com.msegal.tunegame.spotify

interface SpotifyTokenProvider {
    suspend fun exchangeCode(code: String): SpotifyToken

    suspend fun refreshToken(
        refreshToken: String
    ): SpotifyToken
}