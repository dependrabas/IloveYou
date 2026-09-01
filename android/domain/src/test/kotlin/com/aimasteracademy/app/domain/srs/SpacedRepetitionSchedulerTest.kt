package com.aimasteracademy.app.domain.srs

import com.aimasteracademy.app.domain.model.FlashcardReview
import com.aimasteracademy.app.domain.model.RecallGrade
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpacedRepetitionSchedulerTest {

    private val scheduler = SpacedRepetitionScheduler()
    private val today = 1_000L

    @Test
    fun `a new card is due immediately`() {
        val review = scheduler.newReview("card1", today)

        assertThat(review.dueEpochDay).isEqualTo(today)
        assertThat(review.repetitions).isEqualTo(0)
        assertThat(review.isMastered).isFalse()
    }

    @Test
    fun `the first successful recall schedules the card for tomorrow`() {
        val review = scheduler.schedule(scheduler.newReview("card1", today), RecallGrade.KNOWN, today)

        assertThat(review.repetitions).isEqualTo(1)
        assertThat(review.intervalDays).isEqualTo(1)
        assertThat(review.dueEpochDay).isEqualTo(today + 1)
    }

    @Test
    fun `the second successful recall jumps to six days`() {
        var review = scheduler.newReview("card1", today)
        review = scheduler.schedule(review, RecallGrade.KNOWN, today)
        review = scheduler.schedule(review, RecallGrade.KNOWN, today + 1)

        assertThat(review.repetitions).isEqualTo(2)
        assertThat(review.intervalDays).isEqualTo(6)
    }

    @Test
    fun `intervals grow after the second review`() {
        var review = scheduler.newReview("card1", today)
        var day = today
        val intervals = mutableListOf<Int>()

        repeat(5) {
            review = scheduler.schedule(review, RecallGrade.KNOWN, day)
            intervals += review.intervalDays
            day += review.intervalDays
        }

        assertThat(intervals).isInStrictOrder()
        assertThat(intervals.last()).isGreaterThan(6)
    }

    @Test
    fun `easy recalls grow the interval faster than normal ones`() {
        var known = scheduler.newReview("card1", today)
        var easy = scheduler.newReview("card2", today)

        repeat(3) {
            known = scheduler.schedule(known, RecallGrade.KNOWN, today)
            easy = scheduler.schedule(easy, RecallGrade.EASY, today)
        }

        assertThat(easy.intervalDays).isGreaterThan(known.intervalDays)
        assertThat(easy.easeFactor).isGreaterThan(known.easeFactor)
    }

    @Test
    fun `a failed recall resets the interval and counts a lapse`() {
        var review = scheduler.newReview("card1", today)
        repeat(3) { review = scheduler.schedule(review, RecallGrade.KNOWN, today) }
        val easeBeforeLapse = review.easeFactor

        val lapsed = scheduler.schedule(review, RecallGrade.AGAIN, today + 20)

        assertThat(lapsed.repetitions).isEqualTo(0)
        assertThat(lapsed.intervalDays).isEqualTo(0)
        assertThat(lapsed.dueEpochDay).isEqualTo(today + 20)
        assertThat(lapsed.lapses).isEqualTo(1)
        // Ease decays but is not reset — prior successes still count for something.
        assertThat(lapsed.easeFactor).isLessThan(easeBeforeLapse)
        assertThat(lapsed.easeFactor).isGreaterThan(1.29f)
    }

    @Test
    fun `the ease factor never drops below the floor that would strand a card`() {
        var review = scheduler.newReview("card1", today)

        repeat(20) { review = scheduler.schedule(review, RecallGrade.AGAIN, today) }

        assertThat(review.easeFactor).isAtLeast(1.3f)
        // The card can still graduate again after repeated failures.
        val recovered = scheduler.schedule(review, RecallGrade.KNOWN, today)
        assertThat(recovered.intervalDays).isAtLeast(1)
    }

    @Test
    fun `intervals are capped at a year`() {
        var review = scheduler.newReview("card1", today)
        var day = today

        repeat(30) {
            review = scheduler.schedule(review, RecallGrade.EASY, day)
            day += review.intervalDays
        }

        assertThat(review.intervalDays).isAtMost(365)
    }

    @Test
    fun `a card becomes mastered only after sustained success`() {
        var review = scheduler.newReview("card1", today)
        var day = today

        repeat(3) {
            review = scheduler.schedule(review, RecallGrade.KNOWN, day)
            day += review.intervalDays
        }
        assertThat(review.isMastered).isFalse()

        review = scheduler.schedule(review, RecallGrade.KNOWN, day)
        assertThat(review.repetitions).isAtLeast(FlashcardReview.MASTERY_REPETITIONS)
        assertThat(review.isMastered).isTrue()
    }

    @Test
    fun `due cards are those scheduled on or before today hardest first`() {
        val reviews = listOf(
            FlashcardReview("easy", easeFactor = 2.6f, dueEpochDay = today - 1),
            FlashcardReview("hard", easeFactor = 1.4f, dueEpochDay = today),
            FlashcardReview("future", easeFactor = 2.0f, dueEpochDay = today + 5),
        )

        val due = scheduler.dueCards(reviews, today)

        assertThat(due.map { it.flashcardId }).containsExactly("hard", "easy").inOrder()
    }
}
