package com.msegal.tunegame.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameSessionTest {

    private val songs = listOf(
        Song("Come Together", "Abbey Road", "The Beatles"),
        Song("Money", "The Dark Side of the Moon", "Pink Floyd"),
        Song("Dreams", "Rumours", "Fleetwood Mac"),
        Song("Roxanne", "Outlandos d'Amour", "The Police")
    )

    private val testSong = Song(
        song = "Come Together",
        album = "Abbey Road",
        artist = "The Beatles"
    )

    @Test
    fun `elapsed time is calculated correctly`() {

        var fakeTime = 0L

        val session = GameSession(
            engine = GameEngine(songs),
            clock = { fakeTime }
        )

        val question = Question(
            question = "Who wrote Come Together?",
            choices = emptyList(),
            correctAnswer = "The Beatles",
            type = QuestionType.SONG_TO_ARTIST
        )

        session.startQuestion(
            question = question,
            song = testSong
        )

        fakeTime = 5_000_000_000L

        assertEquals(
            5.0,
            session.elapsedSeconds()
        )
    }

    @Test
    fun `question can be cleared`() {

        var fakeTime = 0L

        val session = GameSession(
            engine = GameEngine(songs),
            clock = { fakeTime }
        )

        val question = Question(
            question = "Who wrote Come Together?",
            choices = emptyList(),
            correctAnswer = "The Beatles",
            type = QuestionType.SONG_TO_ARTIST
        )

        session.startQuestion(
            question = question,
            song = testSong
        )

        session.clearQuestion()

        assertNull(session.currentSong)
        assertNull(session.currentQuestion)
        assertEquals(0.0, session.elapsedSeconds())
    }
}