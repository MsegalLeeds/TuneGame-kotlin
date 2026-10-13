package com.msegal.tunegame.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistMetadata(
    val id: String,
    val name: String
)

@Serializable
internal data class SpotifyUserPlaylistsResponse(
    val items: List<SpotifyPlaylistMetadata?>,
    val next: String? = null
)
