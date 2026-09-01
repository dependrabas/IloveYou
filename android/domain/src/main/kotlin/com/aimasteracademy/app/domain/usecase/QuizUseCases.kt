package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.gamification.AchievementContext
import com.aimasteracademy.app.domain.gamification.AchievementEvaluator
import com.aimasteracademy.app.domain.gamification.LevelSystem
import com.aimasteracademy.app.domain.gamification.XpEngine
import com.aimasteracademy.app.domain.model.Achievement
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.LearnerLevel
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuizAttempt
import com.aimasteracademy.app.domain.model.QuizMode
import com.aimasteracademy.app.domain.model.XpEvent
import com.aimasteracademy.app.domain.quiz.QuestionSelector
import com.aimasteracademy.app.domain.quiz.QuizScorer
import com.aimasteracademy.app.domain.quiz.QuizSummary
import com.aimasteracademy.app.domain.quiz.SelectionSpec
import com.aimasteracademy.app.domain.repository.GamificationRepository
import com.aimasteracademy.app.domain.repository.QuestionRepository
import com.aimasteracademy.app.domain.repository.QuizRepository
import com.aimasteracademy.app.domain.repository.UserRepository
import com.aimasteracademy.app.domain.util.DomainError
import com.aimasteracademy.app.domain.util.Outcome
import kotlinx.coroutines.flow.first

/**
 * Builds the question list for a quiz in any [QuizMode].
 *
 * Centralising this keeps every practice surface consistent: the same
 * anti-repetition and difficulty rules apply whether the learner tapped Quick
 * Quiz or the Daily Challenge.
 */
class BuildQuizUseCase(
    private val questionRepository: QuestionRepository,
    private val quizRepository: QuizRepository,
    private val selector: QuestionSelector = QuestionSelector(),
) {
    suspend operator fun invoke(
        mode: QuizMode,
        count: Int,
        categories: Set<Category> = emptySet(),
        difficulties: Set<Difficulty> = emptySet(),
        explicitQuestionIds: List<String> = emptyList(),
        seed: Long? = null,
    ): Outcome<List<Question>> {
        val required = when (mode) {
            QuizMode.MISTAKE_REVIEW -> questionRepository.incorrectlyAnsweredIds().take(count)
            else -> explicitQuestionIds
        }

        // Mistake review with nothing to review is an empty state, not an error
        // the learner should see as a failure.
        if (mode == QuizMode.MISTAKE_REVIEW && required.isEmpty()) {
            return Outcome.success(emptyList())
        }

        val pool = questionRepository.pool(categories, difficulties)
        if (pool.isEmpty() && required.isEmpty()) {
            return Outcome.failure(DomainError.ContentUnavailable)
        }

        val questions = selector.select(
            pool = pool + questionRepository.getQuestions(required),
            spec = SelectionSpec(
                count = count,
                categories = categories,
                difficulties = difficulties,
                recentlySeenIds = questionRepository.recentlySeenIds(),
                requiredIds = required,
                seed = seed,
            ),
        ).map(selector::shuffleOptions)

        return Outcome.success(questions)
    }

    /** The ten-question daily challenge, identical for everyone on [epochDay]. */
    suspend fun dailyChallenge(epochDay: Long): Outcome<List<Question>> {
        val challenge = quizRepository.dailyChallenge(epochDay)
        return when (challenge) {
            is Outcome.Failure -> challenge
            is Outcome.Success -> invoke(
                mode = QuizMode.DAILY_CHALLENGE,
                count = challenge.data.questionIds.size.coerceAtLeast(DAILY_CHALLENGE_SIZE),
                explicitQuestionIds = challenge.data.questionIds,
                // Seeding by day makes the challenge deterministic and shareable.
                seed = epochDay,
            )
        }
    }

    private companion object {
        const val DAILY_CHALLENGE_SIZE = 10
    }
}

/** The XP, level-up and achievements produced by finishing a quiz. */
data class QuizCompletionResult(
    val summary: QuizSummary,
    val xpAwarded: Int,
    val newLevel: LearnerLevel?,
    val unlockedAchievements: List<Achievement>,
    val streakDays: Int,
    val streakMilestoneXp: Int,
)

/**
 * Persists a finished attempt and applies every consequence: XP, streak, level
 * and achievements.
 *
 * Written as one use case so that a quiz can never be half-recorded — the
 * dashboard cannot end up showing XP the ledger does not contain.
 */
class CompleteQuizUseCase(
    private val quizRepository: QuizRepository,
    private val gamificationRepository: GamificationRepository,
    private val questionRepository: QuestionRepository,
    private val userRepository: UserRepository,
    private val scorer: QuizScorer = QuizScorer(),
    private val xpEngine: XpEngine = XpEngine(),
    private val achievementEvaluator: AchievementEvaluator = AchievementEvaluator(),
) {
    suspend operator fun invoke(
        attempt: QuizAttempt,
        todayEpochDay: Long,
    ): Outcome<QuizCompletionResult> {
        val questions = questionRepository.getQuestions(attempt.questionIds)
        val summary = scorer.summarise(attempt, questions)

        val xpBefore = gamificationRepository.totalXp.first()

        when (val saved = quizRepository.saveAttempt(attempt.copy(xpEarned = summary.totalXp))) {
            is Outcome.Failure -> return saved
            is Outcome.Success -> Unit
        }

        // The completion bonus; per-answer XP was already banked as it was earned.
        gamificationRepository.awardXp(
            xpEngine.award(
                event = if (attempt.mode == QuizMode.DAILY_CHALLENGE) {
                    XpEvent.DAILY_CHALLENGE_COMPLETED
                } else {
                    XpEvent.QUIZ_COMPLETED
                },
                amount = summary.completionXp,
                reason = attempt.title,
                categoryId = attempt.category?.id,
            ),
        )

        val streakDays = when (val streak = gamificationRepository.recordStudyDay(todayEpochDay)) {
            is Outcome.Success -> streak.data
            is Outcome.Failure -> 0
        }
        val milestoneXp = xpEngine.forStreakMilestone(streakDays)
        if (milestoneXp > 0) {
            gamificationRepository.awardXp(
                xpEngine.award(
                    event = XpEvent.STREAK_MILESTONE_7,
                    amount = milestoneXp,
                    reason = "$streakDays-day streak",
                ),
            )
        }

        val xpAfter = gamificationRepository.totalXp.first()
        val stats = userRepository.stats.first()
        val unlocked = achievementEvaluator.evaluate(
            catalogue = gamificationRepository.achievementCatalogue(),
            context = AchievementContext(
                stats = stats,
                dailyChallengesCompleted = quizRepository.dailyChallengesCompletedCount(),
                mockExamsPassed = quizRepository.mockExamsPassedCount(),
            ),
            alreadyUnlockedIds = gamificationRepository.unlockedAchievements.first()
                .map { it.achievementId }
                .toSet(),
        )
        if (unlocked.isNotEmpty()) {
            gamificationRepository.unlockAchievements(unlocked)
        }

        return Outcome.success(
            QuizCompletionResult(
                summary = summary,
                xpAwarded = xpAfter - xpBefore,
                newLevel = LevelSystem.levelUpFrom(xpBefore, xpAfter),
                unlockedAchievements = unlocked,
                streakDays = streakDays,
                streakMilestoneXp = milestoneXp,
            ),
        )
    }
}
