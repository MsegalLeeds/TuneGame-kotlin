package com.msegal.tunegame.game

import kotlinx.serialization.Serializable

@Serializable
data class AnswerResult(
    val correct: Boolean,
    val correctAnswer: String,
    val pointsAwarded: Int,
    val score: Int,
    val streak: Int,
    val lives: Int,
    val gameOver: Boolean
)