package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.gamification.XpEngine
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Certificate
import com.aimasteracademy.app.domain.model.Flashcard
import com.aimasteracademy.app.domain.model.RecallGrade
import com.aimasteracademy.app.domain.model.XpEvent
import com.aimasteracademy.app.domain.repository.CertificateRepository
import com.aimasteracademy.app.domain.repository.ContentRepository
import com.aimasteracademy.app.domain.repository.FlashcardRepository
import com.aimasteracademy.app.domain.repository.GamificationRepository
import com.aimasteracademy.app.domain.repository.ProgressRepository
import com.aimasteracademy.app.domain.srs.SpacedRepetitionScheduler
import com.aimasteracademy.app.domain.util.DomainError
import com.aimasteracademy.app.domain.util.Outcome

/** Loads the cards due for review today, newest-difficulty first. */
class LoadDueFlashcardsUseCase(
    private val flashcardRepository: FlashcardRepository,
) {
    suspend operator fun invoke(
        todayEpochDay: Long,
        category: Category? = null,
        limit: Int = DEFAULT_SESSION_SIZE,
    ): List<Flashcard> = flashcardRepository.dueCards(todayEpochDay, category, limit)

    private companion object {
        const val DEFAULT_SESSION_SIZE = 20
    }
}

/** The outcome of grading one flashcard. */
data class FlashcardReviewResult(
    val nextDueEpochDay: Long,
    val intervalDays: Int,
    val becameMastered: Boolean,
    val xpAwarded: Int,
)

/**
 * Grades a flashcard, advances its spaced-repetition schedule and awards XP the
 * first time a card reaches mastery.
 */
class ReviewFlashcardUseCase(
    private val flashcardRepository: FlashcardRepository,
    private val gamificationRepository: GamificationRepository,
    private val scheduler: SpacedRepetitionScheduler = SpacedRepetitionScheduler(),
    private val xpEngine: XpEngine = XpEngine(),
) {
    suspend operator fun invoke(
        flashcardId: String,
        grade: RecallGrade,
        todayEpochDay: Long,
    ): FlashcardReviewResult {
        val existing = flashcardRepository.reviewState(flashcardId)
            ?: scheduler.newReview(flashcardId, todayEpochDay)
        val wasMastered = existing.isMastered

        val updated = scheduler.schedule(existing, grade, todayEpochDay)
        flashcardRepository.saveReview(updated)

        val becameMastered = updated.isMastered && !wasMastered
        val xp = if (becameMastered) XpEvent.FLASHCARD_MASTERED.baseXp else 0
        if (xp > 0) {
            gamificationRepository.awardXp(
                xpEngine.award(XpEvent.FLASHCARD_MASTERED, xp, "Flashcard mastered"),
            )
        }

        return FlashcardReviewResult(
            nextDueEpochDay = updated.dueEpochDay,
            intervalDays = updated.intervalDays,
            becameMastered = becameMastered,
            xpAwarded = xp,
        )
    }
}

/**
 * Issues a track-completion certificate, but only once every lesson in the
 * track is genuinely finished.
 *
 * The eligibility check lives here rather than in the UI so a certificate can
 * never be minted by navigating to the screen directly.
 */
class IssueCertificateUseCase(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val certificateRepository: CertificateRepository,
) {
    suspend operator fun invoke(trackId: String, scorePercent: Int? = null): Outcome<Certificate> {
        val track = contentRepository.getTrack(trackId)
            ?: return Outcome.failure(DomainError.ContentUnavailable)

        if (trackId !in progressRepository.completedTrackIds()) {
            return Outcome.failure(DomainError.ContentUnavailable)
        }
        // Re-issuing returns the existing certificate so the id stays stable and
        // any previously shared verification link keeps working.
        certificateRepository.observeCertificates()
        return certificateRepository.issueCertificate(track.id, scorePercent)
    }

    /** Whether the Claim Certificate button should be enabled. */
    suspend fun isEligible(trackId: String): Boolean =
        trackId in progressRepository.completedTrackIds()
}
