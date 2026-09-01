package com.aimasteracademy.app.domain.quiz

import com.aimasteracademy.app.domain.gamification.XpEngine
import com.aimasteracademy.app.domain.model.AnswerSubmission
import com.aimasteracademy.app.domain.model.GradedAnswer
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuestionType

/**
 * Grades a submission against a question, for all nine question types.
 *
 * Two rules hold across every type:
 *  1. Grading is total — an empty or malformed submission scores zero rather
 *     than throwing, because a learner skipping a question is normal.
 *  2. The result always carries the correct answer and its explanation, so the
 *     UI can teach the concept instead of just reporting failure.
 */
class AnswerGrader(private val xpEngine: XpEngine = XpEngine()) {

    fun grade(question: Question, submission: AnswerSubmission): GradedAnswer {
        val score = score(question, submission.response)
        val isCorrect = score >= 1f
        val isPartial = score > 0f && score < 1f

        val xp = when {
            isCorrect -> xpEngine.forCorrectAnswer(question.difficulty, submission.usedHint)
            // Partial credit earns partial XP on the types where it applies.
            isPartial -> (xpEngine.forCorrectAnswer(question.difficulty, submission.usedHint) * score).toInt()
            else -> 0
        }

        return GradedAnswer(
            questionId = question.id,
            submission = submission,
            isCorrect = isCorrect,
            isPartiallyCorrect = isPartial,
            score = score,
            xpAwarded = xp,
            correctAnswer = question.correctAnswer,
            explanation = question.explanation,
        )
    }

    /**
     * Returns 0f..1f for [response] against [question].
     *
     * Choice, true/false, code, image and scenario questions are all-or-nothing.
     * Matching and ordering award partial credit, because getting four of five
     * pairs right genuinely reflects partial understanding.
     */
    fun score(question: Question, response: List<String>): Float {
        if (response.isEmpty()) return 0f

        return when (question.type) {
            QuestionType.SINGLE_CHOICE,
            QuestionType.TRUE_FALSE,
            QuestionType.IMAGE_CHOICE,
            QuestionType.CODE,
            QuestionType.SCENARIO,
            -> scoreSingleChoice(question, response)

            QuestionType.MULTIPLE_ANSWER -> scoreMultipleAnswer(question, response)
            QuestionType.FILL_IN_BLANK -> scoreFillInBlank(question, response)
            QuestionType.MATCHING -> scorePartialSet(question, response)
            QuestionType.ORDERING -> scoreOrdering(question, response)
        }
    }

    private fun scoreSingleChoice(question: Question, response: List<String>): Float {
        val picked = response.firstOrNull()?.trim() ?: return 0f
        return if (question.correctAnswer.any { it.trim().equals(picked, ignoreCase = true) }) 1f else 0f
    }

    /**
     * All-or-nothing over an unordered set: every correct option must be
     * selected and no incorrect one may be.
     */
    private fun scoreMultipleAnswer(question: Question, response: List<String>): Float {
        val expected = question.correctAnswer.map { it.trim().lowercase() }.toSet()
        val actual = response.map { it.trim().lowercase() }.toSet()
        return if (expected == actual) 1f else 0f
    }

    /**
     * Free text is normalised before comparison — case, surrounding whitespace,
     * internal runs of whitespace and trailing punctuation are all ignored, so a
     * learner is never marked wrong for typing "Supervised learning." instead of
     * "supervised learning".
     */
    private fun scoreFillInBlank(question: Question, response: List<String>): Float {
        val typed = response.firstOrNull()?.let(::normalise) ?: return 0f
        if (typed.isEmpty()) return 0f
        return if (question.correctAnswer.any { normalise(it) == typed }) 1f else 0f
    }

    /** Partial credit over unordered `"left|right"` pairs. */
    private fun scorePartialSet(question: Question, response: List<String>): Float {
        val expected = question.correctAnswer.map { it.trim().lowercase() }.toSet()
        if (expected.isEmpty()) return 0f
        val actual = response.map { it.trim().lowercase() }.toSet()
        val hits = actual.count { it in expected }
        return (hits.toFloat() / expected.size).coerceIn(0f, 1f)
    }

    /**
     * Partial credit by position: each item in the right slot earns its share.
     * A response of the wrong length can still score, so a learner who nails the
     * first three steps of a pipeline is credited for them.
     */
    private fun scoreOrdering(question: Question, response: List<String>): Float {
        val expected = question.correctAnswer
        if (expected.isEmpty()) return 0f
        val hits = expected.indices.count { index ->
            val actual = response.getOrNull(index)?.trim()
            actual != null && actual.equals(expected[index].trim(), ignoreCase = true)
        }
        return (hits.toFloat() / expected.size).coerceIn(0f, 1f)
    }

    private fun normalise(raw: String): String = raw
        .trim()
        .lowercase()
        .replace(PUNCTUATION, "")
        .replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PUNCTUATION = Regex("[.,;:!?\"']")
    }
}
