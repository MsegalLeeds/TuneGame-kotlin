package com.msegal.tunegame.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnswerMatcherTest {

    @Test
    fun `exact answer matches`() {
        assertTrue(
            AnswerMatcher.isMatch(
                "The Beatles",
                "The Beatles"
            )
        )
    }

    @Test
    fun `answer ignores capitalisation`() {
        assertTrue(
            AnswerMatcher.isMatch(
                "the beatles",
                "The Beatles"
            )
        )
    }

    @Test
    fun `answer ignores surrounding spaces`() {
        assertTrue(
            AnswerMatcher.isMatch(
                "  The Beatles  ",
                "The Beatles"
            )
        )
    }

    @Test
    fun `small spelling mistake is accepted`() {
        assertTrue(
            AnswerMatcher.isMatch(
                "The Beatls",
                "The Beatles"
            )
        )
    }

    @Test
    fun `completely different answer is rejected`() {
        assertFalse(
            AnswerMatcher.isMatch(
                "Pink Floyd",
                "The Beatles"
            )
        )
    }
}