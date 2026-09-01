package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.fake.FakeFlashcardRepository
import com.aimasteracademy.app.domain.fake.FakeGamificationRepository
import com.aimasteracademy.app.domain.model.FlashcardReview
import com.aimasteracademy.app.domain.model.RecallGrade
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReviewFlashcardUseCaseTest {

    @Test
    fun `an unseen card is created and scheduled on first review`() = runTest {
        val cards = FakeFlashcardRepository()
        val subject = ReviewFlashcardUseCase(cards, FakeGamificationRepository())

        val result = subject.invoke("card1", RecallGrade.KNOWN, todayEpochDay = 500)

        assertThat(result.intervalDays).isEqualTo(1)
        assertThat(result.nextDueEpochDay).isEqualTo(501)
        assertThat(cards.savedReviews).hasSize(1)
    }

    @Test
    fun `mastery is awarded once and only once`() = runTest {
        val cards = FakeFlashcardRepository()
        val gamification = FakeGamificationRepository()
        val subject = ReviewFlashcardUseCase(cards, gamification)

        var day = 500L
        val masteryDays = mutableListOf<Int>()
        repeat(6) {
            val result = subject.invoke("card1", RecallGrade.KNOWN, day)
            if (result.becameMastered) masteryDays += result.xpAwarded
            day += result.intervalDays
        }

        assertThat(masteryDays).hasSize(1)
        assertThat(gamification.awards.count { it.event.name == "FLASHCARD_MASTERED" }).isEqualTo(1)
    }

    @Test
    fun `a forgotten card is rescheduled for today and awards nothing`() = runTest {
        val cards = FakeFlashcardRepository().apply {
            setReview(FlashcardReview("card1", easeFactor = 2.5f, intervalDays = 10, repetitions = 3))
        }
        val gamification = FakeGamificationRepository()

        val result = ReviewFlashcardUseCase(cards, gamification)
            .invoke("card1", RecallGrade.AGAIN, todayEpochDay = 600)

        assertThat(result.intervalDays).isEqualTo(0)
        assertThat(result.nextDueEpochDay).isEqualTo(600)
        assertThat(result.xpAwarded).isEqualTo(0)
        assertThat(gamification.totalXp.first()).isEqualTo(0)
    }
}
