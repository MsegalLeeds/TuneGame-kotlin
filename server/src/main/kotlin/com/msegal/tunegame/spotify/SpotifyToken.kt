package com.msegal.tunegame.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyToken(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("token_type")
    val tokenType: String,

    @SerialName("expires_in")
    val expiresIn: Int,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    val scope: String = ""
)