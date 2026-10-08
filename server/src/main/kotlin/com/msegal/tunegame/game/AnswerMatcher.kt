package com.msegal.tunegame.game

object AnswerMatcher {

    const val ERROR_THRESHOLD = 0.6

    fun isMatch(
        answer: String,
        correctAnswer: String
    ): Boolean {
        val normalisedAnswer = answer.trim().lowercase()
        val normalisedCorrect = correctAnswer.trim().lowercase()

        if (normalisedAnswer == normalisedCorrect) {
            return true
        }

        val similarity = similarity(
            normalisedAnswer,
            normalisedCorrect
        )

        return similarity >= ERROR_THRESHOLD
    }

    private fun similarity(
        first: String,
        second: String
    ): Double {

        if (first.isEmpty() && second.isEmpty()) {
            return 1.0
        }

        val distance = levenshteinDistance(first, second)

        val longestLength = maxOf(
            first.length,
            second.length
        )

        if (longestLength == 0) {
            return 1.0
        }

        return 1.0 - (
                distance.toDouble() / longestLength
                )
    }

    private fun levenshteinDistance(
        first: String,
        second: String
    ): Int {

        val previous = IntArray(second.length + 1) {
            it
        }

        val current = IntArray(second.length + 1)

        for (i in first.indices) {

            current[0] = i + 1

            for (j in second.indices) {

                val cost =
                    if (first[i] == second[j]) 0 else 1

                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }

            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }

        return previous[second.length]
    }
}