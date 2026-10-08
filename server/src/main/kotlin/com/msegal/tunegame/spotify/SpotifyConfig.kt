package com.msegal.tunegame.spotify

data class SpotifyConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String
) {
    companion object {
        fun fromEnvironmentOrNull(): SpotifyConfig? {
            val clientId =
                System.getenv("SPOTIFY_CLIENT_ID")
                    ?: return null

            val clientSecret =
                System.getenv("SPOTIFY_CLIENT_SECRET")
                    ?: return null

            val redirectUri =
                System.getenv("SPOTIFY_REDIRECT_URI")
                    ?: return null

            return SpotifyConfig(
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri
            )
        }
    }
}