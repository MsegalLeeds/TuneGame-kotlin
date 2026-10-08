package com.msegal.tunegame.score

import kotlinx.serialization.Serializable

@Serializable
data class Score(
    val id: String,
    val playerName: String,
    val score: Int
)