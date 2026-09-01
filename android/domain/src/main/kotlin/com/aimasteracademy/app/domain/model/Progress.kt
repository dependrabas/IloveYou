package com.aimasteracademy.app.domain.model

/** Where a learner stands on a single lesson. */
data class LessonProgress(
    val lessonId: String,
    val courseId: String,
    val trackId: String,
    val status: ProgressStatus,
    val scrollFraction: Float = 0f,
    val secondsSpent: Int = 0,
    val completedAtEpochMillis: Long? = null,
    val lastOpenedAtEpochMillis: Long = 0L,
)

enum class ProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
}

/** Rolled-up progress for a course, computed from its lessons. */
data class CourseProgress(
    val courseId: String,
    val totalLessons: Int,
    val completedLessons: Int,
    val lastLessonId: String?,
) {
    val fraction: Float
        get() = if (totalLessons == 0) 0f else completedLessons.toFloat() / totalLessons

    val isComplete: Boolean get() = totalLessons > 0 && completedLessons >= totalLessons
}

/** A finished (or in-flight) run through a set of questions. */
data class QuizAttempt(
    val id: String,
    val mode: QuizMode,
    val title: String,
    val category: Category?,
    val questionIds: List<String>,
    val gradedAnswers: List<GradedAnswer>,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val xpEarned: Int,
) {
    val totalQuestions: Int get() = questionIds.size
    val correctCount: Int get() = gradedAnswers.count { it.isCorrect }
    val incorrectCount: Int get() = gradedAnswers.count { !it.isCorrect }
    val skippedCount: Int get() = (totalQuestions - gradedAnswers.size).coerceAtLeast(0)

    val accuracy: Float
        get() = if (totalQuestions == 0) 0f else correctCount.toFloat() / totalQuestions

    val accuracyPercent: Int get() = (accuracy * 100).toInt()

    val durationMillis: Long
        get() = (finishedAtEpochMillis ?: startedAtEpochMillis) - startedAtEpochMillis

    val isPerfect: Boolean get() = totalQuestions > 0 && correctCount == totalQuestions
}

/** The practice surfaces a quiz can be launched from. */
enum class QuizMode(val id: String) {
    QUICK("quick"),
    TOPIC("topic"),
    DIFFICULTY("difficulty"),
    RANDOM("random"),
    TIMED("timed"),
    EXAM("exam"),
    MISTAKE_REVIEW("mistake_review"),
    SAVED("saved"),
    DAILY_CHALLENGE("daily_challenge"),
    LESSON_CHECK("lesson_check"),
    DAILY_QUESTION("daily_question");

    /** Exam mode withholds per-question feedback until the whole paper is submitted. */
    val revealsAnswerImmediately: Boolean
        get() = this != EXAM && this != TIMED

    companion object {
        fun fromId(id: String): QuizMode = entries.firstOrNull { it.id == id } ?: QUICK
    }
}

/** Configuration for a certification-style mock exam. */
data class ExamBlueprint(
    val id: String,
    val title: String,
    val description: String,
    val questionCount: Int,
    val durationMinutes: Int,
    val passMarkPercent: Int,
    val categoryWeights: Map<Category, Int>,
    val certificateTitle: String,
) {
    init {
        require(questionCount > 0) { "Exam $id must have at least one question" }
        require(passMarkPercent in 1..100) { "Exam $id pass mark must be 1..100" }
    }
}

/** The graded outcome of a mock exam, including a topic-by-topic breakdown. */
data class ExamResult(
    val blueprintId: String,
    val attempt: QuizAttempt,
    val passMarkPercent: Int,
    val topicBreakdown: List<TopicPerformance>,
    val weakCategories: List<Category>,
    val recommendedLessonIds: List<String>,
) {
    val passed: Boolean get() = attempt.accuracyPercent >= passMarkPercent
}

/** The once-a-day, ten-question challenge. */
data class DailyChallenge(
    val id: String,
    val epochDay: Long,
    val title: String,
    val questionIds: List<String>,
    val xpReward: Int,
    val bonusXpForPerfect: Int,
)

/** The daily one-off question shown on the dashboard. */
data class DailyQuestion(
    val epochDay: Long,
    val questionId: String,
)

/** A single educational fact surfaced on the dashboard each day. */
data class AiFact(
    val id: String,
    val text: String,
    val category: Category,
    val relatedLessonId: String? = null,
)
