package com.msegal.tunegame.api

import com.msegal.tunegame.game.Question
import kotlinx.serialization.Serializable

@Serializable
data class NewGameResponse(
    val ok: Boolean,
    val message: String,
    val gameId: String,
    val lives: Int
)

@Serializable
data class QuestionResponse(
    val question: Question
)

@Serializable
data class AnswerRequest(
    val gameId: String,
    val answer: String
)

@Serializable
data class ErrorResponse(
    val error: String
)