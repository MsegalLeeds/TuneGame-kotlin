package com.msegal.tunegame.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistItemsResponse(
    val items: List<SpotifyPlaylistItem>,
    val next: String? = null
)

@Serializable
data class SpotifyPlaylistItem(
    val item: SpotifyPlaylistTrack? = null
)

@Serializable
data class SpotifyPlaylistTrack(
    val name: String,
    val artists: List<SpotifyArtist>,
    val album: SpotifyPlaylistAlbum
)

@Serializable
data class SpotifyPlaylistAlbum(
    val name: String
)