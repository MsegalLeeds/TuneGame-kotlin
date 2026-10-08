package com.msegal.tunegame.game

import kotlinx.serialization.Serializable

@Serializable
enum class QuestionType {
    SONG_TO_ALBUM,
    SONG_TO_ARTIST,
    ALBUM_TO_ARTIST
}

@Serializable
data class Question(
    val question: String,
    val choices: List<String>,
    val correctAnswer: String,
    val type: QuestionType
)