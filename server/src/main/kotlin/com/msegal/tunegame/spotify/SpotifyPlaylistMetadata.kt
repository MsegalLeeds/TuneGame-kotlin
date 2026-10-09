package com.msegal.tunegame.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistMetadata(
    val id: String,
    val name: String
)