package com.msegal.tunegame.shared.api

import kotlinx.serialization.Serializable

@Serializable
enum class QuestionType {
    SONG_TO_ALBUM,
    SONG_TO_ARTIST,
    ALBUM_TO_ARTIST
}

@Serializable
data class NewGameResponse(
    val ok: Boolean,
    val message: String,
    val gameId: String,
    val lives: Int
)

@Serializable
data class NewGameRequest(
    val playlistId: String? = null
)

@Serializable
data class PublicQuestion(
    val question: String,
    val choices: List<String>,
    val type: QuestionType,
    val albumArtUrl: String? = null
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
data class AnswerResult(
    val correct: Boolean,
    val correctAnswer: String,
    val pointsAwarded: Int,
    val score: Int,
    val streak: Int,
    val lives: Int,
    val gameOver: Boolean
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val spotifyPlaylistId: String
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val spotifyPlaylistId: String
)

@Serializable
data class Score(
    val id: String,
    val playerName: String,
    val score: Int
)

@Serializable
data class CreateScoreRequest(
    val playerName: String,
    val gameId: String
)

@Serializable
data class SpotifyStatusResponse(
    val connected: Boolean
)

@Serializable
data class ErrorResponse(
    val error: String
)