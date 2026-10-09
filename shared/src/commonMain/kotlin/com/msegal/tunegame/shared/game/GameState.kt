package com.msegal.tunegame.shared.game

import com.msegal.tunegame.shared.api.PublicQuestion

data class GameState(
    val gameId: String? = null,
    val question: PublicQuestion? = null,
    val score: Int = 0,
    val streak: Int = 0,
    val lives: Int = 3,
    val gameOver: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)