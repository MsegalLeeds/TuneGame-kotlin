package com.msegal.tunegame.api

import kotlinx.serialization.Serializable
import com.msegal.tunegame.game.QuestionType

@Serializable
data class NewGameResponse(
    val ok: Boolean,
    val message: String,
    val gameId: String,
    val lives: Int
)

@Serializable
data class QuestionResponse(
    val question: PublicQuestion
)

@Serializable
data class AnswerRequest(
    val gameId: String,
    val answer: String
)

@Serializable
data class PublicQuestion(
    val question: String,
    val choices: List<String>,
    val type: QuestionType
)
@Serializable
data class ErrorResponse(
    val error: String
)
@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val spotifyPlaylistId: String
)