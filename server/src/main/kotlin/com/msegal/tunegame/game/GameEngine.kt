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

    fun generateQuestion(type: QuestionType): Question {
        require(songs.isNotEmpty()) {
            "No songs available"
        }

        val song = songs.random()

        val question = when (type) {

            QuestionType.SONG_TO_ALBUM -> Question(
                question = "Name the album that ${song.song} is on?",
                choices = generateChoices(song.album) { it.album },
                correctAnswer = song.album,
                type = type
            )

            QuestionType.SONG_TO_ARTIST -> Question(
                question = "Name the artist who wrote ${song.song}?",
                choices = generateChoices(song.artist) { it.artist },
                correctAnswer = song.artist,
                type = type
            )

            QuestionType.ALBUM_TO_ARTIST -> Question(
                question = "Name the artist who made ${song.album}?",
                choices = generateChoices(song.artist) { it.artist },
                correctAnswer = song.artist,
                type = type
            )
        }

        state.questionsAsked++

        return question
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