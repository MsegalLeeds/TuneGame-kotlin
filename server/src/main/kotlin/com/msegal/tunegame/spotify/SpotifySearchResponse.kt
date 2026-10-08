package com.msegal.tunegame.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTracks
)

@Serializable
data class SpotifyTracks(
    val items: List<SpotifyTrackItem>
)

@Serializable
data class SpotifyTrackItem(
    val id: String,
    val name: String,
    val uri: String,
    val artists: List<SpotifyArtist>
)

@Serializable
data class SpotifyArtist(
    val name: String
)