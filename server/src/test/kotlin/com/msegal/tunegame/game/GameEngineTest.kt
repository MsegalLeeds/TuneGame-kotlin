package com.msegal.tunegame.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GameEngineTest {

    private val songs = listOf(
        Song("Come Together", "Abbey Road", "The Beatles"),
        Song("Money", "The Dark Side of the Moon", "Pink Floyd"),
        Song("Dreams", "Rumours", "Fleetwood Mac"),
        Song("Roxanne", "Outlandos d'Amour", "The Police")
    )

    @Test
    fun `new game resets state`() {
        val engine = GameEngine(songs)

        engine.state.score = 100
        engine.state.streak = 5
        engine.state.lives = 1

        engine.newGame()

        assertEquals(0, engine.state.score)
        assertEquals(0, engine.state.streak)
        assertEquals(0, engine.state.questionsAsked)
        assertEquals(3, engine.state.lives)
    }

    @Test
    fun `song to album generates four choices`() {
        val engine = GameEngine(songs)

        val question = engine.generateQuestion(
            QuestionType.SONG_TO_ALBUM
        )

        assertEquals(4, question.choices.size)
        assertTrue(question.correctAnswer in question.choices)
    }

    @Test
    fun `song to artist generates four choices`() {
        val engine = GameEngine(songs)

        val question = engine.generateQuestion(
            QuestionType.SONG_TO_ARTIST
        )

        assertEquals(4, question.choices.size)
        assertTrue(question.correctAnswer in question.choices)
    }

    @Test
    fun `correct answer awards points`() {
        val engine = GameEngine(songs)

        val question = Question(
            question = "Test question",
            choices = listOf(
                "Abbey Road",
                "Rumours",
                "Outlandos d'Amour",
                "The Dark Side of the Moon"
            ),
            correctAnswer = "Abbey Road",
            type = QuestionType.SONG_TO_ALBUM
        )

        val result = engine.checkAnswer(
            "Abbey Road",
            question
        )

        assertTrue(result.correct)
        assertEquals(10, result.pointsAwarded)
        assertEquals(10, result.score)
        assertEquals(1, result.streak)
        assertEquals(3, result.lives)
    }

    @Test
    fun `incorrect answer loses a life`() {
        val engine = GameEngine(songs)

        val question = Question(
            question = "Test question",
            choices = emptyList(),
            correctAnswer = "Abbey Road",
            type = QuestionType.SONG_TO_ALBUM
        )

        val result = engine.checkAnswer(
            "Rumours",
            question
        )

        assertFalse(result.correct)
        assertEquals(0, result.score)
        assertEquals(0, result.streak)
        assertEquals(2, result.lives)
    }

    @Test
    fun `streak increases points`() {
        val engine = GameEngine(songs)

        val question = Question(
            question = "Test question",
            choices = emptyList(),
            correctAnswer = "Abbey Road",
            type = QuestionType.SONG_TO_ALBUM
        )

        val first = engine.checkAnswer("Abbey Road", question)
        val second = engine.checkAnswer("Abbey Road", question)
        val third = engine.checkAnswer("Abbey Road", question)

        assertEquals(10, first.pointsAwarded)
        assertEquals(15, second.pointsAwarded)
        assertEquals(20, third.pointsAwarded)

        assertEquals(45, engine.state.score)
        assertEquals(3, engine.state.streak)
    }

    @Test
    fun `game ends after losing all lives`() {
        val engine = GameEngine(songs)

        val question = Question(
            question = "Test question",
            choices = emptyList(),
            correctAnswer = "Abbey Road",
            type = QuestionType.SONG_TO_ALBUM
        )

        engine.checkAnswer("wrong", question)
        engine.checkAnswer("wrong", question)
        val result = engine.checkAnswer("wrong", question)

        assertEquals(0, result.lives)
        assertTrue(result.gameOver)
    }
}