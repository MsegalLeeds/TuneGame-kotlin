package com.msegal.tunegame.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaybackRequest(
    val uris: List<String>,
    @SerialName("position_ms")
    val positionMs: Int
)