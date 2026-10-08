package com.msegal.tunegame.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTrack(
    val id: String,
    val name: String,
    val artist: String,
    val uri: String,
    val albumArtUrl: String? = null
)