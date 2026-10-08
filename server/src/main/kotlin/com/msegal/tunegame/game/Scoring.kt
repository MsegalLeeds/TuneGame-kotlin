package com.msegal.tunegame.game
import kotlin.math.roundToInt

object Scoring {

    const val POINTS_PER_QUESTION = 10
    const val STREAK_BONUS = 5
    const val MIN_MULTIPLIER = 0.3

    fun calculatePoints(
        streak: Int,
        elapsedSeconds: Double,
        timeLimitSeconds: Double = 30.0
    ): Int {

        val timeMultiplier = (
                1.0 - (elapsedSeconds / timeLimitSeconds)
                ).coerceIn(
                MIN_MULTIPLIER,
                1.0
            )

        val basePoints =
            POINTS_PER_QUESTION +
                    (streak * STREAK_BONUS)

        return (basePoints * timeMultiplier).roundToInt()
    }
}