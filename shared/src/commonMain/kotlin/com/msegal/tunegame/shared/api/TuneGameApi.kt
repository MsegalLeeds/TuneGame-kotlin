package com.msegal.tunegame.shared.api

interface TuneGameApi {

    suspend fun newGame(
        playlistId: String? = null
    ): NewGameResponse

    suspend fun getQuestion(
        gameId: String
    ): QuestionResponse

    suspend fun submitAnswer(
        gameId: String,
        answer: String
    ): AnswerResult

    suspend fun getPlaylists():
            List<Playlist>

    suspend fun createPlaylist(
        name: String,
        spotifyPlaylistId: String
    ): Playlist

    suspend fun deletePlaylist(
        id: String
    )

    suspend fun getScores():
            List<Score>

    suspend fun submitScore(
        playerName: String,
        gameId: String
    ): Score
}