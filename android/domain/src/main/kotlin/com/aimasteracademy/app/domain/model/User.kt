package com.aimasteracademy.app.domain.model

/**
 * The signed-in (or guest) learner.
 *
 * A guest is a fully functional local account: it studies, earns XP and keeps a
 * streak. Signing in later merges that local progress into the cloud profile
 * rather than discarding it.
 */
data class User(
    val id: String,
    val name: String,
    val email: String?,
    val username: String,
    val avatarRef: String?,
    val isGuest: Boolean,
    val isEmailVerified: Boolean,
    val joinedAtEpochDay: Long,
    val plan: SubscriptionPlan = SubscriptionPlan.FREE,
    val profile: LearnerProfile = LearnerProfile(),
)

/** Personalisation captured during onboarding; drives the learning path. */
data class LearnerProfile(
    val knowledgeLevel: KnowledgeLevel = KnowledgeLevel.BEGINNER,
    val motivation: LearningMotivation = LearningMotivation.CURIOUS,
    val goals: List<LearningGoal> = emptyList(),
    val dailyGoal: DailyGoal = DailyGoal.STEADY,
    val interestedCategories: List<Category> = emptyList(),
    val isComplete: Boolean = false,
)

enum class LearningGoal(val id: String) {
    UNDERSTAND_BASICS("understand_basics"),
    BUILD_PROJECTS("build_projects"),
    PASS_INTERVIEWS("pass_interviews"),
    USE_AI_AT_WORK("use_ai_at_work"),
    MASTER_PROMPTING("master_prompting"),
    LEARN_ML_MATH("learn_ml_math"),
    STAY_CURRENT("stay_current"),
    TEACH_OTHERS("teach_others");

    companion object {
        private val byId = entries.associateBy(LearningGoal::id)
        fun fromId(id: String): LearningGoal? = byId[id]
    }
}

/**
 * Monetisation is architected but dormant: everything ships as [FREE] until a
 * billing implementation is explicitly enabled, so no screen is ever gated today.
 */
enum class SubscriptionPlan {
    FREE,
    PREMIUM;

    val hasPremiumAccess: Boolean get() = this == PREMIUM
}

/** Aggregate lifetime statistics shown on the profile and analytics screens. */
data class UserStats(
    val totalXp: Int = 0,
    val lessonsCompleted: Int = 0,
    val questionsAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val quizzesCompleted: Int = 0,
    val perfectQuizzes: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val studyTimeMinutes: Int = 0,
    val certificatesEarned: Int = 0,
    val badgesEarned: Int = 0,
    val flashcardsMastered: Int = 0,
) {
    /** Share of answered questions that were correct, as 0f..1f. */
    val accuracy: Float
        get() = if (questionsAnswered == 0) 0f else correctAnswers.toFloat() / questionsAnswered

    val accuracyPercent: Int get() = (accuracy * 100).toInt()
}

/** Per-category performance, the raw material for weak-topic recommendations. */
data class TopicPerformance(
    val category: Category,
    val answered: Int,
    val correct: Int,
    val lessonsCompleted: Int,
    val lastPracticedEpochDay: Long?,
) {
    val accuracy: Float
        get() = if (answered == 0) 0f else correct.toFloat() / answered

    /**
     * Whether the topic has enough evidence to be judged at all. Calling a topic
     * "weak" after two questions would be noise, not insight.
     */
    val hasEnoughEvidence: Boolean get() = answered >= MIN_ANSWERS_FOR_SIGNAL

    companion object {
        const val MIN_ANSWERS_FOR_SIGNAL = 5
    }
}
