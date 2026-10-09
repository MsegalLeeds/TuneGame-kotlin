package com.msegal.tunegame.shared.game

import com.msegal.tunegame.shared.api.TuneGameApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameClient(
    private val api: TuneGameApi
) {

    private val _state =
        MutableStateFlow(GameState())

    val state: StateFlow<GameState> =
        _state.asStateFlow()

    suspend fun newGame(
        playlistId: String? = null
    ): GameState {
        _state.value = _state.value.copy(
            isLoading = true,
            error = null
        )

        return try {
            val response =
                api.newGame(playlistId)

            val newState = GameState(
                gameId = response.gameId,
                lives = response.lives,
                isLoading = false,
                error = null
            )

            _state.value = newState
            newState

        } catch (e: Exception) {
            val newState = GameState(
                isLoading = false,
                error = e.message ?: "Unable to start game"
            )

            _state.value = newState
            newState
        }
    }

    suspend fun nextQuestion(): GameState {
        val gameId =
            _state.value.gameId
                ?: error("No active game")

        _state.value = _state.value.copy(
            isLoading = true,
            error = null
        )

        return try {
            val response =
                api.getQuestion(gameId)

            val newState =
                _state.value.copy(
                    question = response.question,
                    isLoading = false,
                    error = null
                )

            _state.value = newState
            newState

        } catch (e: Exception) {
            val newState =
                _state.value.copy(
                    isLoading = false,
                    error =
                        e.message
                            ?: "Unable to load question"
                )

            _state.value = newState
            newState
        }
    }

    suspend fun answer(
        answer: String
    ): GameState {

        val gameId =
            _state.value.gameId
                ?: error("No active game")

        _state.value = _state.value.copy(
            isLoading = true,
            error = null
        )

        return try {
            val result =
                api.submitAnswer(
                    gameId = gameId,
                    answer = answer
                )

            val newState =
                _state.value.copy(
                    question = null,
                    score = result.score,
                    streak = result.streak,
                    lives = result.lives,
                    gameOver = result.gameOver,
                    isLoading = false,
                    error = null
                )

            _state.value = newState
            newState

        } catch (e: Exception) {
            val newState =
                _state.value.copy(
                    isLoading = false,
                    error =
                        e.message
                            ?: "Unable to submit answer"
                )

            _state.value = newState
            newState
        }
    }

    suspend fun submitScore(
        playerName: String
    ) {
        val currentState =
            _state.value

        val gameId =
            currentState.gameId
                ?: error("No active game")

        if (!currentState.gameOver) {
            error("Game is not over")
        }

        _state.value =
            currentState.copy(
                isLoading = true,
                error = null
            )

        try {
            api.submitScore(
                playerName = playerName,
                gameId = gameId
            )

            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = null
                )

        } catch (e: Exception) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error =
                        e.message
                            ?: "Unable to submit score"
                )
        }
    }
}