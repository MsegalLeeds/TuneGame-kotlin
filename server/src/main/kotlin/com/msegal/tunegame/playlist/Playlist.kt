package com.msegal.tunegame.playlist

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val spotifyPlaylistId: String
)