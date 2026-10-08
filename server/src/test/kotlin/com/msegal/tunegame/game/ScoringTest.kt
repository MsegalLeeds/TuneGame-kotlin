package com.msegal.tunegame.game

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoringTest {

    @Test
    fun `instant answer gets full points`() {

        val points = Scoring.calculatePoints(
            streak = 0,
            elapsedSeconds = 0.0
        )

        assertEquals(10, points)
    }

    @Test
    fun `streak increases available points`() {

        val points = Scoring.calculatePoints(
            streak = 2,
            elapsedSeconds = 0.0
        )

        assertEquals(20, points)
    }

    @Test
    fun `slower answer earns fewer points`() {

        val fast = Scoring.calculatePoints(
            streak = 0,
            elapsedSeconds = 1.0
        )

        val slow = Scoring.calculatePoints(
            streak = 0,
            elapsedSeconds = 20.0
        )

        assert(fast > slow)
    }

    @Test
    fun `points cannot fall below minimum multiplier`() {

        val points = Scoring.calculatePoints(
            streak = 0,
            elapsedSeconds = 100.0
        )

        assertEquals(3, points)
    }

    @Test
    fun `answer at 29 seconds receives minimum points`() {

        val points = Scoring.calculatePoints(
            streak = 0,
            elapsedSeconds = 29.0
        )

        assertEquals(3, points)
    }

    @Test
    fun `answer at 30 seconds receives minimum points`() {

        val points = Scoring.calculatePoints(
            streak = 0,
            elapsedSeconds = 30.0
        )

        assertEquals(3, points)
    }

    @Test
    fun `answer after time limit still calculates minimum points`() {

        val points = Scoring.calculatePoints(
            streak = 0,
            elapsedSeconds = 31.0
        )

        assertEquals(3, points)
    }
}