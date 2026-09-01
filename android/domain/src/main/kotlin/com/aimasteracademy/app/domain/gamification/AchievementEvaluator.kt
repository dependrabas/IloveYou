package com.aimasteracademy.app.domain.gamification

import com.aimasteracademy.app.domain.model.Achievement
import com.aimasteracademy.app.domain.model.AchievementCriteria
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.TopicPerformance
import com.aimasteracademy.app.domain.model.UserStats

/**
 * Everything the evaluator needs to judge every criterion in one pass.
 *
 * Passing a single snapshot (rather than a repository) keeps evaluation pure and
 * makes it cheap to run after every answer.
 */
data class AchievementContext(
    val stats: UserStats,
    val topicPerformance: Map<Category, TopicPerformance> = emptyMap(),
    val completedCourseIds: Set<String> = emptySet(),
    val completedTrackIds: Set<String> = emptySet(),
    val dailyChallengesCompleted: Int = 0,
    val mockExamsPassed: Int = 0,
)

/** Evaluates declarative [AchievementCriteria] against a live [AchievementContext]. */
class AchievementEvaluator {

    /**
     * Returns the achievements newly satisfied by [context], excluding anything
     * already in [alreadyUnlockedIds].
     *
     * Results are ordered by tier so a burst of unlocks celebrates the most
     * impressive one first.
     */
    fun evaluate(
        catalogue: List<Achievement>,
        context: AchievementContext,
        alreadyUnlockedIds: Set<String>,
    ): List<Achievement> = catalogue
        .asSequence()
        .filter { it.id !in alreadyUnlockedIds }
        .filter { isSatisfied(it.criteria, context) }
        .sortedByDescending { it.tier.ordinal }
        .toList()

    /** Progress toward an unlock as 0f..1f, for the "almost there" UI. */
    fun progressToward(criteria: AchievementCriteria, context: AchievementContext): Float {
        val stats = context.stats
        fun ratio(current: Int, target: Int) =
            if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)

        return when (criteria) {
            is AchievementCriteria.LessonsCompleted -> ratio(stats.lessonsCompleted, criteria.count)
            is AchievementCriteria.CorrectAnswers -> ratio(stats.correctAnswers, criteria.count)
            is AchievementCriteria.QuestionsAnswered -> ratio(stats.questionsAnswered, criteria.count)
            is AchievementCriteria.StreakDays -> ratio(stats.longestStreak, criteria.days)
            is AchievementCriteria.TotalXp -> ratio(stats.totalXp, criteria.xp)
            is AchievementCriteria.PerfectQuizzes -> ratio(stats.perfectQuizzes, criteria.count)
            is AchievementCriteria.FlashcardsMastered -> ratio(stats.flashcardsMastered, criteria.count)
            is AchievementCriteria.DailyChallengesCompleted ->
                ratio(context.dailyChallengesCompleted, criteria.count)
            is AchievementCriteria.MockExamsPassed -> ratio(context.mockExamsPassed, criteria.count)
            is AchievementCriteria.CourseCompleted ->
                if (criteria.courseId in context.completedCourseIds) 1f else 0f
            is AchievementCriteria.TrackCompleted ->
                if (criteria.trackId in context.completedTrackIds) 1f else 0f
            is AchievementCriteria.CategoryAccuracy -> {
                val perf = context.topicPerformance[criteria.category] ?: return 0f
                // Progress is gated on volume first: accuracy over too few
                // answers is not yet meaningful.
                val volume = ratio(perf.answered, criteria.minAnswered)
                val quality = if (criteria.minAccuracy <= 0f) 1f
                else (perf.accuracy / criteria.minAccuracy).coerceIn(0f, 1f)
                minOf(volume, quality)
            }
        }
    }

    private fun isSatisfied(criteria: AchievementCriteria, context: AchievementContext): Boolean {
        val stats = context.stats
        return when (criteria) {
            is AchievementCriteria.LessonsCompleted -> stats.lessonsCompleted >= criteria.count
            is AchievementCriteria.CorrectAnswers -> stats.correctAnswers >= criteria.count
            is AchievementCriteria.QuestionsAnswered -> stats.questionsAnswered >= criteria.count
            is AchievementCriteria.StreakDays -> stats.longestStreak >= criteria.days
            is AchievementCriteria.TotalXp -> stats.totalXp >= criteria.xp
            is AchievementCriteria.PerfectQuizzes -> stats.perfectQuizzes >= criteria.count
            is AchievementCriteria.FlashcardsMastered -> stats.flashcardsMastered >= criteria.count
            is AchievementCriteria.DailyChallengesCompleted ->
                context.dailyChallengesCompleted >= criteria.count
            is AchievementCriteria.MockExamsPassed -> context.mockExamsPassed >= criteria.count
            is AchievementCriteria.CourseCompleted -> criteria.courseId in context.completedCourseIds
            is AchievementCriteria.TrackCompleted -> criteria.trackId in context.completedTrackIds
            is AchievementCriteria.CategoryAccuracy -> {
                val perf = context.topicPerformance[criteria.category] ?: return false
                perf.answered >= criteria.minAnswered && perf.accuracy >= criteria.minAccuracy
            }
        }
    }
}
