package com.aimasteracademy.app.domain.quiz

import com.aimasteracademy.app.domain.gamification.XpEngine
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.ExamBlueprint
import com.aimasteracademy.app.domain.model.ExamResult
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuizAttempt
import com.aimasteracademy.app.domain.model.TopicPerformance

/** A qualitative band for a finished quiz, used to pick copy and celebration. */
enum class ScoreBand(val minPercent: Int) {
    PERFECT(100),
    EXCELLENT(80),
    GOOD(60),
    FAIR(40),
    NEEDS_WORK(0);

    /** Confetti is reserved for genuinely good results so it keeps its meaning. */
    val deservesCelebration: Boolean get() = this == PERFECT || this == EXCELLENT

    companion object {
        fun forPercent(percent: Int): ScoreBand =
            entries.first { percent >= it.minPercent }
    }
}

/** A finished attempt plus everything the results screen needs to render. */
data class QuizSummary(
    val attempt: QuizAttempt,
    val band: ScoreBand,
    val completionXp: Int,
    val totalXp: Int,
    val topicBreakdown: List<TopicPerformance>,
    val weakCategories: List<Category>,
)

/**
 * Turns a completed attempt into a summary: totals, XP, per-topic breakdown and
 * the weak areas that drive recommendations.
 */
class QuizScorer(private val xpEngine: XpEngine = XpEngine()) {

    fun summarise(attempt: QuizAttempt, questions: List<Question>): QuizSummary {
        val completionXp = xpEngine.forQuizCompletion(attempt)
        val answerXp = attempt.gradedAnswers.sumOf { it.xpAwarded }
        val breakdown = topicBreakdown(attempt, questions)

        return QuizSummary(
            attempt = attempt,
            band = ScoreBand.forPercent(attempt.accuracyPercent),
            completionXp = completionXp,
            totalXp = completionXp + answerXp,
            topicBreakdown = breakdown,
            weakCategories = weakCategories(breakdown),
        )
    }

    /** Grades a mock exam against its [blueprint]. */
    fun gradeExam(
        blueprint: ExamBlueprint,
        attempt: QuizAttempt,
        questions: List<Question>,
        recommendedLessonIds: List<String>,
    ): ExamResult {
        val breakdown = topicBreakdown(attempt, questions)
        return ExamResult(
            blueprintId = blueprint.id,
            attempt = attempt,
            passMarkPercent = blueprint.passMarkPercent,
            topicBreakdown = breakdown,
            weakCategories = weakCategories(breakdown),
            recommendedLessonIds = recommendedLessonIds,
        )
    }

    /**
     * Per-category accuracy within a single attempt.
     *
     * Only categories the attempt actually covered appear, so the results screen
     * never shows an empty row for a topic that was not tested.
     */
    fun topicBreakdown(attempt: QuizAttempt, questions: List<Question>): List<TopicPerformance> {
        val byId = questions.associateBy(Question::id)
        return attempt.gradedAnswers
            .mapNotNull { graded -> byId[graded.questionId]?.let { it.category to graded } }
            .groupBy({ it.first }, { it.second })
            .map { (category, answers) ->
                TopicPerformance(
                    category = category,
                    answered = answers.size,
                    correct = answers.count { it.isCorrect },
                    lessonsCompleted = 0,
                    lastPracticedEpochDay = null,
                )
            }
            .sortedBy { it.accuracy }
    }

    /**
     * Categories worth recommending revision for.
     *
     * Requires at least two answers in the category so a single unlucky miss
     * does not brand a topic as weak.
     */
    fun weakCategories(
        breakdown: List<TopicPerformance>,
        threshold: Float = WEAK_ACCURACY_THRESHOLD,
    ): List<Category> = breakdown
        .filter { it.answered >= MIN_ANSWERS_TO_JUDGE && it.accuracy < threshold }
        .sortedBy { it.accuracy }
        .map { it.category }

    private companion object {
        const val WEAK_ACCURACY_THRESHOLD = 0.7f
        const val MIN_ANSWERS_TO_JUDGE = 2
    }
}
