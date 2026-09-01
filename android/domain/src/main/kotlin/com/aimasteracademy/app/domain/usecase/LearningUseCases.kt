package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.gamification.AchievementContext
import com.aimasteracademy.app.domain.gamification.AchievementEvaluator
import com.aimasteracademy.app.domain.gamification.LevelSystem
import com.aimasteracademy.app.domain.gamification.XpEngine
import com.aimasteracademy.app.domain.model.Achievement
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.DailyGoal
import com.aimasteracademy.app.domain.model.KnowledgeLevel
import com.aimasteracademy.app.domain.model.LearnerLevel
import com.aimasteracademy.app.domain.model.LearningGoal
import com.aimasteracademy.app.domain.model.LearningMotivation
import com.aimasteracademy.app.domain.model.LearningTrack
import com.aimasteracademy.app.domain.model.LearnerProfile
import com.aimasteracademy.app.domain.model.Lesson
import com.aimasteracademy.app.domain.model.ProgressStatus
import com.aimasteracademy.app.domain.model.XpEvent
import com.aimasteracademy.app.domain.recommendation.LessonRecommendation
import com.aimasteracademy.app.domain.recommendation.RecommendationEngine
import com.aimasteracademy.app.domain.recommendation.RecommendationInput
import com.aimasteracademy.app.domain.repository.ContentRepository
import com.aimasteracademy.app.domain.repository.GamificationRepository
import com.aimasteracademy.app.domain.repository.ProgressRepository
import com.aimasteracademy.app.domain.repository.UserRepository
import com.aimasteracademy.app.domain.util.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The consequences of finishing a lesson. */
data class LessonCompletionResult(
    val xpAwarded: Int,
    val newLevel: LearnerLevel?,
    val unlockedAchievements: List<Achievement>,
    val streakDays: Int,
    val courseCompleted: Boolean,
)

/**
 * Marks a lesson complete and applies XP, streak and achievement effects.
 *
 * Mirrors [CompleteQuizUseCase] so both completion paths share the same rules.
 */
class CompleteLessonUseCase(
    private val progressRepository: ProgressRepository,
    private val gamificationRepository: GamificationRepository,
    private val contentRepository: ContentRepository,
    private val userRepository: UserRepository,
    private val xpEngine: XpEngine = XpEngine(),
    private val achievementEvaluator: AchievementEvaluator = AchievementEvaluator(),
) {
    suspend operator fun invoke(lesson: Lesson, todayEpochDay: Long): Outcome<LessonCompletionResult> {
        // Completing an already-complete lesson must not award XP twice.
        if (progressRepository.getLessonStatus(lesson.id) == ProgressStatus.COMPLETED) {
            return Outcome.success(
                LessonCompletionResult(0, null, emptyList(), 0, courseCompleted = false),
            )
        }

        val xpBefore = gamificationRepository.totalXp.first()

        when (val completed = progressRepository.completeLesson(lesson)) {
            is Outcome.Failure -> return completed
            is Outcome.Success -> Unit
        }

        gamificationRepository.awardXp(
            xpEngine.award(
                event = XpEvent.LESSON_COMPLETED,
                amount = xpEngine.forLessonCompleted(lesson.estimatedMinutes),
                reason = lesson.title,
                categoryId = lesson.category.id,
            ),
        )

        val courseLessons = contentRepository.getLessonsForCourse(lesson.courseId)
        val courseCompleted = courseLessons.isNotEmpty() &&
            courseLessons.all { progressRepository.getLessonStatus(it.id) == ProgressStatus.COMPLETED }
        if (courseCompleted) {
            gamificationRepository.awardXp(
                xpEngine.award(
                    event = XpEvent.COURSE_COMPLETED,
                    amount = XpEvent.COURSE_COMPLETED.baseXp,
                    reason = "Course complete",
                    categoryId = lesson.category.id,
                ),
            )
        }

        val streakDays = when (val streak = gamificationRepository.recordStudyDay(todayEpochDay)) {
            is Outcome.Success -> streak.data
            is Outcome.Failure -> 0
        }
        val milestoneXp = xpEngine.forStreakMilestone(streakDays)
        if (milestoneXp > 0) {
            gamificationRepository.awardXp(
                xpEngine.award(XpEvent.STREAK_MILESTONE_7, milestoneXp, "$streakDays-day streak"),
            )
        }

        userRepository.recordStudyTime(lesson.estimatedMinutes)

        val xpAfter = gamificationRepository.totalXp.first()
        val unlocked = achievementEvaluator.evaluate(
            catalogue = gamificationRepository.achievementCatalogue(),
            context = AchievementContext(
                stats = userRepository.stats.first(),
                completedCourseIds = progressRepository.completedCourseIds(),
                completedTrackIds = progressRepository.completedTrackIds(),
            ),
            alreadyUnlockedIds = gamificationRepository.unlockedAchievements.first()
                .map { it.achievementId }
                .toSet(),
        )
        if (unlocked.isNotEmpty()) gamificationRepository.unlockAchievements(unlocked)

        return Outcome.success(
            LessonCompletionResult(
                xpAwarded = xpAfter - xpBefore,
                newLevel = LevelSystem.levelUpFrom(xpBefore, xpAfter),
                unlockedAchievements = unlocked,
                streakDays = streakDays,
                courseCompleted = courseCompleted,
            ),
        )
    }
}

/** Streams personalised lesson recommendations for the dashboard. */
class ObserveRecommendationsUseCase(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val userRepository: UserRepository,
    private val engine: RecommendationEngine = RecommendationEngine(),
) {
    operator fun invoke(todayEpochDay: Long, limit: Int = 5): Flow<List<LessonRecommendation>> =
        combine(
            contentRepository.observeAllLessons(),
            progressRepository.observeLessonProgress(),
            progressRepository.observeTopicPerformance(),
            userRepository.profile,
            progressRepository.observeInProgressLesson(),
        ) { lessons, progress, performance, profile, inProgress ->
            engine.recommend(
                input = RecommendationInput(
                    profile = profile,
                    allLessons = lessons,
                    lessonStatus = progress.associate { it.lessonId to it.status },
                    topicPerformance = performance.associateBy { it.category },
                    inProgressLessonId = inProgress?.id,
                    todayEpochDay = todayEpochDay,
                ),
                limit = limit,
            )
        }
}

/**
 * Builds the learning path shown at the end of onboarding.
 *
 * The path is an ordering of the existing tracks rather than generated content:
 * it puts the track that matches the learner's level and motivation first, then
 * their stated interests, then the remainder in curriculum order.
 */
class GeneratePersonalisedPathUseCase(
    private val contentRepository: ContentRepository,
) {
    suspend operator fun invoke(profile: LearnerProfile): List<LearningTrack> {
        val tracks = contentRepository.observeTracks().first()
        val interests = profile.interestedCategories.toSet()
        val motivationCategories = categoriesFor(profile.motivation)
        val goalCategories = profile.goals.flatMap(::categoriesFor).toSet()

        return tracks.sortedWith(
            compareByDescending<LearningTrack> { track ->
                var score = 0
                if (track.level == profile.knowledgeLevel) score += 40
                // A beginner should still see beginner tracks first even if their
                // interests point somewhere advanced.
                if (profile.knowledgeLevel == KnowledgeLevel.BEGINNER &&
                    track.level == KnowledgeLevel.BEGINNER
                ) {
                    score += 20
                }
                if (track.category in interests) score += 30
                if (track.category in motivationCategories) score += 15
                if (track.category in goalCategories) score += 10
                score
            }.thenBy { it.order },
        )
    }

    /** The daily XP target implied by the learner's chosen study duration. */
    fun dailyXpTarget(goal: DailyGoal): Int = goal.targetXp

    private fun categoriesFor(motivation: LearningMotivation): Set<Category> = when (motivation) {
        LearningMotivation.DEVELOPER -> setOf(
            Category.PYTHON_FOR_AI, Category.MACHINE_LEARNING, Category.AI_AGENTS, Category.RAG,
        )
        LearningMotivation.RESEARCHER -> setOf(
            Category.DEEP_LEARNING, Category.NLP, Category.COMPUTER_VISION,
        )
        LearningMotivation.BUSINESS_OWNER -> setOf(
            Category.GENERATIVE_AI, Category.AI_ETHICS, Category.PROMPT_ENGINEERING,
        )
        LearningMotivation.TEACHER -> setOf(Category.AI_FUNDAMENTALS, Category.AI_ETHICS)
        LearningMotivation.PROFESSIONAL, LearningMotivation.CAREER_CHANGE -> setOf(
            Category.GENERATIVE_AI, Category.PROMPT_ENGINEERING, Category.MACHINE_LEARNING,
        )
        LearningMotivation.STUDENT -> setOf(
            Category.AI_FUNDAMENTALS, Category.MACHINE_LEARNING, Category.DEEP_LEARNING,
        )
        LearningMotivation.ENTHUSIAST, LearningMotivation.CURIOUS -> setOf(
            Category.AI_FUNDAMENTALS, Category.GENERATIVE_AI, Category.LLMS,
        )
    }

    private fun categoriesFor(goal: LearningGoal): Set<Category> = when (goal) {
        LearningGoal.UNDERSTAND_BASICS -> setOf(Category.AI_FUNDAMENTALS)
        LearningGoal.BUILD_PROJECTS -> setOf(Category.PYTHON_FOR_AI, Category.MACHINE_LEARNING)
        LearningGoal.PASS_INTERVIEWS -> setOf(Category.MACHINE_LEARNING, Category.DEEP_LEARNING)
        LearningGoal.USE_AI_AT_WORK -> setOf(Category.GENERATIVE_AI, Category.PROMPT_ENGINEERING)
        LearningGoal.MASTER_PROMPTING -> setOf(Category.PROMPT_ENGINEERING, Category.LLMS)
        LearningGoal.LEARN_ML_MATH -> setOf(Category.MACHINE_LEARNING, Category.DATA_SCIENCE)
        LearningGoal.STAY_CURRENT -> setOf(Category.GENERATIVE_AI, Category.AI_AGENTS)
        LearningGoal.TEACH_OTHERS -> setOf(Category.AI_FUNDAMENTALS, Category.AI_ETHICS)
    }
}

/** Weekly-goal progress for the dashboard ring. */
data class WeeklyGoalProgress(
    val xpThisWeek: Int,
    val targetXp: Int,
    val daysStudiedThisWeek: Int,
) {
    val fraction: Float
        get() = if (targetXp <= 0) 0f else (xpThisWeek.toFloat() / targetXp).coerceIn(0f, 1f)

    val isMet: Boolean get() = xpThisWeek >= targetXp
}

/** Tracks progress toward the learner's weekly XP target. */
class ObserveWeeklyGoalUseCase(
    private val gamificationRepository: GamificationRepository,
    private val userRepository: UserRepository,
) {
    operator fun invoke(weekStartEpochMillis: Long): Flow<WeeklyGoalProgress> =
        combine(
            gamificationRepository.xpLedger,
            userRepository.profile,
        ) { ledger, profile ->
            val thisWeek = ledger.filter { it.awardedAtEpochMillis >= weekStartEpochMillis }
            WeeklyGoalProgress(
                xpThisWeek = thisWeek.sumOf { it.amount },
                targetXp = profile.dailyGoal.targetXp * DAYS_PER_WEEK,
                daysStudiedThisWeek = thisWeek
                    .map { it.awardedAtEpochMillis / MILLIS_PER_DAY }
                    .distinct()
                    .size,
            )
        }

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

/** Level and XP for the header, derived from the XP ledger. */
class ObserveLevelProgressUseCase(
    private val gamificationRepository: GamificationRepository,
) {
    operator fun invoke() = gamificationRepository.totalXp.map(LevelSystem::progressFor)
}
