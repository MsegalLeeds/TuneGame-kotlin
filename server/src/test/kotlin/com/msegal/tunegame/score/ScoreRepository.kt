package com.msegal.tunegame.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScoreRepositoryTest {

    @Test
    fun `score can be created`() {
        val repository = ScoreRepository()

        val score = repository.create(
            playerName = "Marc",
            score = 100
        )

        assertEquals("Marc", score.playerName)
        assertEquals(100, score.score)
    }

    @Test
    fun `scores are returned highest first`() {
        val repository = ScoreRepository()

        repository.create("Player One", 50)
        repository.create("Player Two", 200)
        repository.create("Player Three", 100)

        val scores = repository.getAll()

        assertEquals(200, scores[0].score)
        assertEquals(100, scores[1].score)
        assertEquals(50, scores[2].score)
    }

    @Test
    fun `blank player name is rejected`() {
        val repository = ScoreRepository()

        assertFailsWith<IllegalArgumentException> {
            repository.create("", 100)
        }
    }

    @Test
    fun `negative score is rejected`() {
        val repository = ScoreRepository()

        assertFailsWith<IllegalArgumentException> {
            repository.create("Marc", -1)
        }
    }
}