package com.aimasteracademy.app.domain.recommendation

import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.KnowledgeLevel
import com.aimasteracademy.app.domain.model.LearnerProfile
import com.aimasteracademy.app.domain.model.Lesson
import com.aimasteracademy.app.domain.model.ProgressStatus
import com.aimasteracademy.app.domain.model.TopicPerformance

/** Why a lesson is being recommended — surfaced verbatim in the UI. */
enum class RecommendationReason {
    /** Accuracy in this category is below par. */
    WEAK_TOPIC,

    /** Picks up exactly where the learner left off. */
    CONTINUE_COURSE,

    /** Matches a category the learner said they cared about. */
    MATCHES_INTEREST,

    /** The natural next step at the learner's declared level. */
    NEXT_IN_PATH,

    /** Not opened in a while; worth refreshing. */
    REFRESHER,
}

data class LessonRecommendation(
    val lesson: Lesson,
    val reason: RecommendationReason,
    val rationale: String,
    val score: Float,
)

/**
 * Everything the engine reasons over. Passed as one snapshot so recommendations
 * stay a pure function of observable state.
 */
data class RecommendationInput(
    val profile: LearnerProfile,
    val allLessons: List<Lesson>,
    val lessonStatus: Map<String, ProgressStatus>,
    val topicPerformance: Map<Category, TopicPerformance>,
    val inProgressLessonId: String? = null,
    val todayEpochDay: Long = 0L,
)

/**
 * Chooses what the learner should study next.
 *
 * The ranking is deliberately explainable rather than opaque: each candidate
 * accumulates a score from a handful of named signals, and the strongest signal
 * becomes the [RecommendationReason] shown to the learner. "You answered 4
 * Neural Network questions incorrectly" is far more motivating than an
 * unexplained suggestion.
 */
class RecommendationEngine {

    fun recommend(input: RecommendationInput, limit: Int = 5): List<LessonRecommendation> {
        val incomplete = input.allLessons.filter {
            input.lessonStatus[it.id] != ProgressStatus.COMPLETED
        }
        if (incomplete.isEmpty()) return refresherRecommendations(input, limit)

        val weakCategories = weakCategories(input.topicPerformance)
        val interests = input.profile.interestedCategories.toSet()
        val suitable = Difficulty.suitableFor(input.profile.knowledgeLevel)

        return incomplete
            .map { lesson -> scoreLesson(lesson, input, weakCategories, interests, suitable) }
            .filter { it.score > 0f }
            .sortedWith(compareByDescending<LessonRecommendation> { it.score }.thenBy { it.lesson.order })
            .take(limit)
    }

    /**
     * The single headline recommendation for the dashboard, biased toward
     * resuming an in-progress lesson over starting something new.
     */
    fun continueLearning(input: RecommendationInput): LessonRecommendation? {
        input.inProgressLessonId
            ?.let { id -> input.allLessons.firstOrNull { it.id == id } }
            ?.let { lesson ->
                return LessonRecommendation(
                    lesson = lesson,
                    reason = RecommendationReason.CONTINUE_COURSE,
                    rationale = "Pick up where you left off in ${lesson.title}.",
                    score = MAX_SCORE,
                )
            }
        return recommend(input, limit = 1).firstOrNull()
    }

    /** Categories where measured accuracy is below the weak-topic threshold. */
    fun weakCategories(performance: Map<Category, TopicPerformance>): List<Category> =
        performance.values
            .filter { it.hasEnoughEvidence && it.accuracy < WEAK_THRESHOLD }
            .sortedBy { it.accuracy }
            .map { it.category }

    /** A human-readable nudge such as the one shown on the analytics screen. */
    fun weakTopicMessage(performance: TopicPerformance, categoryLabel: String): String {
        val missed = performance.answered - performance.correct
        return "You missed $missed of ${performance.answered} $categoryLabel questions " +
            "(${(performance.accuracy * 100).toInt()}% accuracy). A quick review would help."
    }

    private fun scoreLesson(
        lesson: Lesson,
        input: RecommendationInput,
        weakCategories: List<Category>,
        interests: Set<Category>,
        suitable: Set<Difficulty>,
    ): LessonRecommendation {
        var score = BASE_SCORE
        var reason = RecommendationReason.NEXT_IN_PATH
        var rationale = "The next step on your ${lesson.title.substringBefore(':')} path."

        // Strongest signal: measured weakness in this lesson's category.
        val weakIndex = weakCategories.indexOf(lesson.category)
        if (weakIndex >= 0) {
            score += WEAK_TOPIC_WEIGHT - weakIndex * WEAK_RANK_DECAY
            reason = RecommendationReason.WEAK_TOPIC
            val perf = input.topicPerformance[lesson.category]
            rationale = if (perf != null) {
                val missed = perf.answered - perf.correct
                "You answered $missed ${lesson.category.id.replace('_', ' ')} questions " +
                    "incorrectly. Review ${lesson.title}."
            } else {
                "Strengthen your weakest topic with ${lesson.title}."
            }
        }

        // Next: the lesson is already open.
        if (input.lessonStatus[lesson.id] == ProgressStatus.IN_PROGRESS) {
            score += IN_PROGRESS_WEIGHT
            reason = RecommendationReason.CONTINUE_COURSE
            rationale = "You're partway through ${lesson.title}."
        }

        // Then: declared interests.
        if (lesson.category in interests) {
            score += INTEREST_WEIGHT
            if (reason == RecommendationReason.NEXT_IN_PATH) {
                reason = RecommendationReason.MATCHES_INTEREST
                rationale = "Matches the topics you chose when you joined."
            }
        }

        // Difficulty fit keeps beginners out of expert content and stops
        // advanced learners being fed material they already know.
        score += if (lesson.difficulty in suitable) DIFFICULTY_FIT_WEIGHT else DIFFICULTY_MISMATCH_PENALTY

        // Earlier lessons in a course win ties, so paths are walked in order.
        score += (MAX_ORDER_BONUS - lesson.order.coerceAtMost(MAX_ORDER_BONUS.toInt())) * ORDER_WEIGHT

        // A beginner should not be handed a 40-minute lesson as their next step.
        if (input.profile.knowledgeLevel == KnowledgeLevel.BEGINNER &&
            lesson.estimatedMinutes > input.profile.dailyGoal.minutes * 2
        ) {
            score += LONG_LESSON_PENALTY
        }

        return LessonRecommendation(lesson, reason, rationale, score.coerceAtLeast(0f))
    }

    /**
     * Once everything is complete, suggest the least recently touched lessons so
     * the "recommended" rail never goes empty.
     */
    private fun refresherRecommendations(
        input: RecommendationInput,
        limit: Int,
    ): List<LessonRecommendation> = input.allLessons
        .sortedBy { it.order }
        .take(limit)
        .map {
            LessonRecommendation(
                lesson = it,
                reason = RecommendationReason.REFRESHER,
                rationale = "You've completed everything here — revisit ${it.title} to keep it sharp.",
                score = BASE_SCORE,
            )
        }

    private companion object {
        const val WEAK_THRESHOLD = 0.7f
        const val BASE_SCORE = 1f
        const val MAX_SCORE = 100f
        const val WEAK_TOPIC_WEIGHT = 50f
        const val WEAK_RANK_DECAY = 5f
        const val IN_PROGRESS_WEIGHT = 30f
        const val INTEREST_WEIGHT = 15f
        const val DIFFICULTY_FIT_WEIGHT = 10f
        const val DIFFICULTY_MISMATCH_PENALTY = -12f
        const val ORDER_WEIGHT = 0.5f
        const val MAX_ORDER_BONUS = 20f
        const val LONG_LESSON_PENALTY = -8f
    }
}
