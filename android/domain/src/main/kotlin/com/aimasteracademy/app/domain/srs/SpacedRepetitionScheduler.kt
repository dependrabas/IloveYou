package com.aimasteracademy.app.domain.srs

import com.aimasteracademy.app.domain.model.FlashcardReview
import com.aimasteracademy.app.domain.model.RecallGrade

/**
 * SM-2 spaced repetition, adapted for a two-button flashcard UI.
 *
 * The classic SuperMemo-2 algorithm expects a 0–5 self-rating, which is far more
 * granularity than a learner will give while swiping cards. The three
 * [RecallGrade] values map onto the quality scale instead, keeping the proven
 * interval maths while asking only one easy question of the learner.
 */
class SpacedRepetitionScheduler {

    /**
     * Returns the updated review state after grading a card on [todayEpochDay].
     *
     * A failed recall ([RecallGrade.AGAIN]) resets the interval so the card comes
     * back the same day, but only *decays* the ease factor rather than resetting
     * it — a card you have known ten times and forgot once should not be treated
     * as brand new.
     */
    fun schedule(
        review: FlashcardReview,
        grade: RecallGrade,
        todayEpochDay: Long,
    ): FlashcardReview {
        val ease = nextEaseFactor(review.easeFactor, grade)

        if (grade == RecallGrade.AGAIN) {
            return review.copy(
                easeFactor = ease,
                repetitions = 0,
                intervalDays = 0,
                dueEpochDay = todayEpochDay,
                lapses = review.lapses + 1,
                lastReviewedEpochDay = todayEpochDay,
            )
        }

        val repetitions = review.repetitions + 1
        val interval = when (repetitions) {
            1 -> FIRST_INTERVAL_DAYS
            2 -> SECOND_INTERVAL_DAYS
            else -> (review.intervalDays * ease).toInt().coerceAtLeast(SECOND_INTERVAL_DAYS + 1)
        }.let { if (grade == RecallGrade.EASY) (it * EASY_BONUS).toInt() else it }
            .coerceAtMost(MAX_INTERVAL_DAYS)

        return review.copy(
            easeFactor = ease,
            repetitions = repetitions,
            intervalDays = interval,
            dueEpochDay = todayEpochDay + interval,
            lastReviewedEpochDay = todayEpochDay,
        )
    }

    /** Cards due on or before [todayEpochDay], hardest-first. */
    fun dueCards(reviews: List<FlashcardReview>, todayEpochDay: Long): List<FlashcardReview> =
        reviews.filter { it.dueEpochDay <= todayEpochDay }
            .sortedWith(compareBy({ it.easeFactor }, { it.dueEpochDay }))

    /** A brand-new card, due immediately. */
    fun newReview(flashcardId: String, todayEpochDay: Long): FlashcardReview =
        FlashcardReview(flashcardId = flashcardId, dueEpochDay = todayEpochDay)

    /**
     * The SM-2 ease update, clamped to a sane floor.
     *
     * Without the floor, repeated failures drive the factor toward zero and the
     * card can never graduate again.
     */
    private fun nextEaseFactor(current: Float, grade: RecallGrade): Float {
        val q = grade.quality
        val delta = 0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f)
        return (current + delta).coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
    }

    private companion object {
        const val FIRST_INTERVAL_DAYS = 1
        const val SECOND_INTERVAL_DAYS = 6
        const val MAX_INTERVAL_DAYS = 365
        const val MIN_EASE_FACTOR = 1.3f
        const val MAX_EASE_FACTOR = 2.8f
        const val EASY_BONUS = 1.3f
    }
}
