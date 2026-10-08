package com.msegal.tunegame.game

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    var score: Int = 0,
    var streak: Int = 0,
    var questionsAsked: Int = 0,
    var lives: Int = 3
) {
    val gameOver: Boolean
        get() = lives <= 0
}