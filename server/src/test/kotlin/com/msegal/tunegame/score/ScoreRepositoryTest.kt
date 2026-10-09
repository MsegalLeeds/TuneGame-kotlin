package com.msegal.tunegame.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScoreRepositoryTest {

    @Test
    fun `score can be created`() {
        val repository = ScoreRepository(
            databaseUrl = "jdbc:sqlite::memory:"
        )

        val score = repository.create(
            playerName = "Marc",
            score = 100
        )

        assertEquals("Marc", score.playerName)
        assertEquals(100, score.score)
    }

    @Test
    fun `scores are returned highest first`() {
        val repository = ScoreRepository(
            databaseUrl = "jdbc:sqlite::memory:"
        )

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
        val repository = ScoreRepository(
            databaseUrl = "jdbc:sqlite::memory:"
        )

        assertFailsWith<IllegalArgumentException> {
            repository.create("", 100)
        }
    }

    @Test
    fun `negative score is rejected`() {
        val repository = ScoreRepository(
            databaseUrl = "jdbc:sqlite::memory:"
        )

        assertFailsWith<IllegalArgumentException> {
            repository.create("Marc", -1)
        }
    }

    @Test
    fun `concurrent score writes are safe`() {

        val repository =
            ScoreRepository(
                databaseUrl =
                    "jdbc:sqlite::memory:"
            )

        val executor =
            java.util.concurrent.Executors
                .newFixedThreadPool(8)

        val futures =
            (1..50).map { number ->

                executor.submit {
                    repository.create(
                        playerName =
                            "Player $number",
                        score =
                            number
                    )
                }
            }

        futures.forEach {
            it.get()
        }

        executor.shutdown()

        assertEquals(
            50,
            repository.getAll().size
        )
    }
}