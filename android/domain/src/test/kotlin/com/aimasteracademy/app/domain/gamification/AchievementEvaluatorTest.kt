package com.aimasteracademy.app.domain.gamification

import com.aimasteracademy.app.domain.model.Achievement
import com.aimasteracademy.app.domain.model.AchievementCriteria
import com.aimasteracademy.app.domain.model.AchievementTier
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.TopicPerformance
import com.aimasteracademy.app.domain.model.UserStats
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AchievementEvaluatorTest {

    private val evaluator = AchievementEvaluator()

    private fun achievement(
        id: String,
        criteria: AchievementCriteria,
        tier: AchievementTier = AchievementTier.BRONZE,
    ) = Achievement(
        id = id,
        title = id,
        description = "",
        iconRef = "ic_badge",
        tier = tier,
        xpReward = 100,
        criteria = criteria,
    )

    @Test
    fun `an unmet criterion does not unlock`() {
        val catalogue = listOf(achievement("first_lesson", AchievementCriteria.LessonsCompleted(1)))

        val unlocked = evaluator.evaluate(
            catalogue,
            AchievementContext(UserStats(lessonsCompleted = 0)),
            emptySet(),
        )

        assertThat(unlocked).isEmpty()
    }

    @Test
    fun `a met criterion unlocks exactly once`() {
        val catalogue = listOf(achievement("first_lesson", AchievementCriteria.LessonsCompleted(1)))
        val context = AchievementContext(UserStats(lessonsCompleted = 3))

        assertThat(evaluator.evaluate(catalogue, context, emptySet())).hasSize(1)
        // Already unlocked achievements are never re-reported.
        assertThat(evaluator.evaluate(catalogue, context, setOf("first_lesson"))).isEmpty()
    }

    @Test
    fun `multiple unlocks are ordered by tier so the best is celebrated first`() {
        val catalogue = listOf(
            achievement("bronze", AchievementCriteria.LessonsCompleted(1), AchievementTier.BRONZE),
            achievement("platinum", AchievementCriteria.LessonsCompleted(2), AchievementTier.PLATINUM),
            achievement("silver", AchievementCriteria.LessonsCompleted(3), AchievementTier.SILVER),
        )

        val unlocked = evaluator.evaluate(
            catalogue,
            AchievementContext(UserStats(lessonsCompleted = 10)),
            emptySet(),
        )

        assertThat(unlocked.map { it.id }).containsExactly("platinum", "silver", "bronze").inOrder()
    }

    @Test
    fun `streak achievements use the longest streak not the current one`() {
        val catalogue = listOf(achievement("week", AchievementCriteria.StreakDays(7)))
        // Streak broken today, but the badge was earned.
        val context = AchievementContext(UserStats(currentStreak = 1, longestStreak = 9))

        assertThat(evaluator.evaluate(catalogue, context, emptySet())).hasSize(1)
    }

    @Test
    fun `category accuracy needs both volume and quality`() {
        val criteria = AchievementCriteria.CategoryAccuracy(Category.NLP, minAnswered = 20, minAccuracy = 0.9f)
        val catalogue = listOf(achievement("nlp_pro", criteria))

        // High accuracy, too few answers.
        val tooFew = AchievementContext(
            stats = UserStats(),
            topicPerformance = mapOf(Category.NLP to TopicPerformance(Category.NLP, 5, 5, 0, null)),
        )
        assertThat(evaluator.evaluate(catalogue, tooFew, emptySet())).isEmpty()

        // Plenty of answers, accuracy too low.
        val tooLow = AchievementContext(
            stats = UserStats(),
            topicPerformance = mapOf(Category.NLP to TopicPerformance(Category.NLP, 40, 30, 0, null)),
        )
        assertThat(evaluator.evaluate(catalogue, tooLow, emptySet())).isEmpty()

        val qualified = AchievementContext(
            stats = UserStats(),
            topicPerformance = mapOf(Category.NLP to TopicPerformance(Category.NLP, 40, 38, 0, null)),
        )
        assertThat(evaluator.evaluate(catalogue, qualified, emptySet())).hasSize(1)
    }

    @Test
    fun `course and track completion criteria match by id`() {
        val catalogue = listOf(
            achievement("finish_course", AchievementCriteria.CourseCompleted("c-ml-101")),
            achievement("finish_track", AchievementCriteria.TrackCompleted("t-fundamentals")),
        )

        val unlocked = evaluator.evaluate(
            catalogue,
            AchievementContext(
                stats = UserStats(),
                completedCourseIds = setOf("c-ml-101"),
                completedTrackIds = emptySet(),
            ),
            emptySet(),
        )

        assertThat(unlocked.map { it.id }).containsExactly("finish_course")
    }

    @Test
    fun `progress reports a fraction toward the goal`() {
        val context = AchievementContext(UserStats(correctAnswers = 25))

        assertThat(evaluator.progressToward(AchievementCriteria.CorrectAnswers(100), context))
            .isWithin(0.001f).of(0.25f)
        // Progress is clamped once the goal is exceeded.
        assertThat(evaluator.progressToward(AchievementCriteria.CorrectAnswers(10), context))
            .isEqualTo(1f)
    }

    @Test
    fun `category accuracy progress is gated on volume first`() {
        val criteria = AchievementCriteria.CategoryAccuracy(Category.RAG, minAnswered = 20, minAccuracy = 0.8f)
        val context = AchievementContext(
            stats = UserStats(),
            // Perfect accuracy, but only a quarter of the required volume.
            topicPerformance = mapOf(Category.RAG to TopicPerformance(Category.RAG, 5, 5, 0, null)),
        )

        assertThat(evaluator.progressToward(criteria, context)).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun `progress toward an untouched category is zero rather than an error`() {
        val criteria = AchievementCriteria.CategoryAccuracy(Category.ROBOTICS, 10, 0.8f)

        assertThat(evaluator.progressToward(criteria, AchievementContext(UserStats()))).isEqualTo(0f)
    }
}
