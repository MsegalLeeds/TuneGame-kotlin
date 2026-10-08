package com.msegal.tunegame.game

class GameSession(
    val engine: GameEngine,
    private val clock: () -> Long = System::nanoTime
) {
    var currentQuestion: Question? = null
        private set

    private var questionStartTime: Long? = null

    fun startQuestion(question: Question) {
        currentQuestion = question
        questionStartTime = clock()
    }

    fun elapsedSeconds(): Double {
        val start = questionStartTime ?: return 0.0

        return (clock() - start) / 1_000_000_000.0
    }

    fun clearQuestion() {
        currentQuestion = null
        questionStartTime = null
    }
}