package com.aimasteracademy.app.domain.gamification

import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.QuizAttempt
import com.aimasteracademy.app.domain.model.XpAward
import com.aimasteracademy.app.domain.model.XpEvent

/**
 * The single place XP is calculated.
 *
 * Every award flows through here so the numbers on the dashboard, the results
 * screen and the leaderboard can never disagree.
 */
class XpEngine(private val clock: () -> Long = System::currentTimeMillis) {

    /**
     * XP for one correct answer, scaled by difficulty.
     *
     * Using a hint costs a quarter of the reward: hints should feel available
     * rather than punitive, but not free.
     */
    fun forCorrectAnswer(difficulty: Difficulty, usedHint: Boolean = false): Int {
        val base = (XpEvent.CORRECT_ANSWER.baseXp * difficulty.weight)
        val afterHint = if (usedHint) base * HINT_PENALTY_MULTIPLIER else base
        return afterHint.toInt().coerceAtLeast(1)
    }

    /** XP for finishing a lesson; longer lessons are worth marginally more. */
    fun forLessonCompleted(estimatedMinutes: Int): Int {
        val lengthBonus = (estimatedMinutes / 5).coerceIn(0, 4) * 5
        return XpEvent.LESSON_COMPLETED.baseXp + lengthBonus
    }

    /**
     * Completion XP for a finished [attempt], excluding the per-answer XP that
     * was already awarded while answering.
     *
     * Combines a flat completion reward, an accuracy bonus, and a perfect-score
     * bonus, so a 10/10 feels distinctly better than a 9/10.
     */
    fun forQuizCompletion(attempt: QuizAttempt): Int {
        if (attempt.totalQuestions == 0) return 0
        val completion = when (attempt.mode.id) {
            "daily_challenge" -> XpEvent.DAILY_CHALLENGE_COMPLETED.baseXp
            "daily_question" -> XpEvent.DAILY_QUESTION_ANSWERED.baseXp
            else -> XpEvent.QUIZ_COMPLETED.baseXp
        }
        val accuracyBonus = (attempt.accuracy * ACCURACY_BONUS_MAX).toInt()
        val perfectBonus = if (attempt.isPerfect) XpEvent.PERFECT_QUIZ.baseXp else 0
        return completion + accuracyBonus + perfectBonus
    }

    /** XP for passing a mock exam, scaled by how far above the pass mark it was. */
    fun forExamPassed(scorePercent: Int, passMarkPercent: Int): Int {
        if (scorePercent < passMarkPercent) return 0
        val headroom = (100 - passMarkPercent).coerceAtLeast(1)
        val margin = (scorePercent - passMarkPercent).toFloat() / headroom
        return XpEvent.MOCK_EXAM_PASSED.baseXp + (margin * EXAM_MARGIN_BONUS_MAX).toInt()
    }

    /**
     * The one-off bonus for reaching a streak milestone.
     *
     * Returns 0 on non-milestone days so callers can invoke it unconditionally.
     */
    fun forStreakMilestone(streakDays: Int): Int = when (streakDays) {
        7 -> XpEvent.STREAK_MILESTONE_7.baseXp
        30 -> XpEvent.STREAK_MILESTONE_30.baseXp
        100 -> XpEvent.STREAK_MILESTONE_100.baseXp
        else -> 0
    }

    /** Wraps a raw amount into a ledger entry. */
    fun award(
        event: XpEvent,
        amount: Int,
        reason: String,
        categoryId: String? = null,
    ): XpAward = XpAward(
        event = event,
        amount = amount,
        reason = reason,
        categoryId = categoryId,
        awardedAtEpochMillis = clock(),
    )

    private companion object {
        const val HINT_PENALTY_MULTIPLIER = 0.75f
        const val ACCURACY_BONUS_MAX = 50
        const val EXAM_MARGIN_BONUS_MAX = 250
    }
}
