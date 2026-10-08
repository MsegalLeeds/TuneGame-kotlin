package com.msegal.tunegame.score

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ScoreRepository {

    private val scores =
        ConcurrentHashMap<String, Score>()

    fun getAll(): List<Score> {
        return scores.values
            .sortedByDescending { it.score }
    }

    fun create(
        playerName: String,
        score: Int
    ): Score {

        require(playerName.isNotBlank()) {
            "Player name cannot be blank"
        }

        require(score >= 0) {
            "Score cannot be negative"
        }

        val newScore = Score(
            id = UUID.randomUUID().toString(),
            playerName = playerName,
            score = score
        )

        scores[newScore.id] = newScore

        return newScore
    }
}