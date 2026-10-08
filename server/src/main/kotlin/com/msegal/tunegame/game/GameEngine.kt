package com.msegal.tunegame.game

class GameEngine(
    private val songs: List<Song>
) {
    val state = GameState()

    companion object {
        const val POINTS_PER_QUESTION = 10
        const val STREAK_BONUS = 5
    }

    fun newGame() {
        state.score = 0
        state.streak = 0
        state.questionsAsked = 0
        state.lives = 3
    }

    /**
     * Generates a question without exposing the associated Song.
     *
     * Used by existing game logic and tests.
     */
    fun generateQuestion(
        type: QuestionType
    ): Question {
        return generateQuestionWithSong(type).question
    }

    /**
     * Generates a question together with the Song that was used.
     *
     * The server uses this version when it needs to know which
     * Spotify track should be played.
     */
    fun generateQuestionWithSong(
        type: QuestionType
    ): GeneratedQuestion {

        val song = songs.random()

        val question = when (type) {

            QuestionType.SONG_TO_ALBUM -> {
                Question(
                    question = "Name the album that ${song.song} is on?",
                    choices = generateChoices(
                        correctAnswer = song.album,
                        selector = { it.album }
                    ),
                    correctAnswer = song.album,
                    type = type
                )
            }

            QuestionType.SONG_TO_ARTIST -> {
                Question(
                    question = "Name the artist who wrote ${song.song}?",
                    choices = generateChoices(
                        correctAnswer = song.artist,
                        selector = { it.artist }
                    ),
                    correctAnswer = song.artist,
                    type = type
                )
            }

            QuestionType.ALBUM_TO_ARTIST -> {
                Question(
                    question = "Name the artist who made ${song.album}?",
                    choices = generateChoices(
                        correctAnswer = song.artist,
                        selector = { it.artist }
                    ),
                    correctAnswer = song.artist,
                    type = type
                )
            }
        }

        state.questionsAsked++

        return GeneratedQuestion(
            question = question,
            song = song
        )
    }

    fun checkAnswer(
        answer: String,
        question: Question,
        elapsedSeconds: Double = 0.0
    ): AnswerResult {

        val correct = AnswerMatcher.isMatch(
            answer,
            question.correctAnswer
        )

        var pointsAwarded = 0

        if (correct) {

            pointsAwarded = Scoring.calculatePoints(
                streak = state.streak,
                elapsedSeconds = elapsedSeconds
            )

            state.score += pointsAwarded
            state.streak++

        } else {

            state.streak = 0

            if (state.lives > 0) {
                state.lives--
            }
        }

        return AnswerResult(
            correct = correct,
            correctAnswer = question.correctAnswer,
            pointsAwarded = pointsAwarded,
            score = state.score,
            streak = state.streak,
            lives = state.lives,
            gameOver = state.lives <= 0
        )
    }

    private fun generateChoices(
        correctAnswer: String,
        selector: (Song) -> String
    ): List<String> {

        val wrongAnswers = songs
            .map(selector)
            .filter { it != correctAnswer }
            .distinct()
            .shuffled()
            .take(3)

        return (wrongAnswers + correctAnswer).shuffled()
    }
}