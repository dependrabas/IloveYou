package com.aimasteracademy.app.domain.model

/**
 * The eight learner ranks, from first lesson to the top of the leaderboard.
 *
 * [xpThreshold] is the cumulative lifetime XP at which the rank is reached.
 * Thresholds grow super-linearly so later ranks stay meaningful, but not so
 * steeply that a committed learner stalls.
 */
enum class LearnerLevel(
    val tier: Int,
    val title: String,
    val xpThreshold: Int,
) {
    AI_BEGINNER(1, "AI Beginner", 0),
    AI_EXPLORER(2, "AI Explorer", 500),
    AI_LEARNER(3, "AI Learner", 1_500),
    AI_PRACTITIONER(4, "AI Practitioner", 3_500),
    AI_SPECIALIST(5, "AI Specialist", 7_000),
    AI_EXPERT(6, "AI Expert", 12_500),
    AI_MASTER(7, "AI Master", 20_000),
    AI_LEGEND(8, "AI Legend", 32_000);

    companion object {
        val ordered: List<LearnerLevel> = entries.sortedBy(LearnerLevel::xpThreshold)

        /** The highest rank whose threshold [totalXp] has reached. */
        fun forXp(totalXp: Int): LearnerLevel =
            ordered.last { totalXp >= it.xpThreshold }

        /** The next rank up, or `null` once [AI_LEGEND] is reached. */
        fun nextAfter(level: LearnerLevel): LearnerLevel? =
            ordered.getOrNull(ordered.indexOf(level) + 1)
    }
}

/** A learner's position within their current rank, ready for the XP bar. */
data class LevelProgress(
    val level: LearnerLevel,
    val nextLevel: LearnerLevel?,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpRequiredForNextLevel: Int,
) {
    /** 0f..1f progress toward the next rank; 1f when maxed out. */
    val fraction: Float
        get() = if (xpRequiredForNextLevel <= 0) 1f
        else (xpIntoLevel.toFloat() / xpRequiredForNextLevel).coerceIn(0f, 1f)

    val isMaxLevel: Boolean get() = nextLevel == null

    val xpToNextLevel: Int
        get() = (xpRequiredForNextLevel - xpIntoLevel).coerceAtLeast(0)
}

/** Every action that can award XP, with its base value. */
enum class XpEvent(val baseXp: Int) {
    LESSON_COMPLETED(20),
    CORRECT_ANSWER(5),
    DAILY_CHALLENGE_COMPLETED(50),
    DAILY_QUESTION_ANSWERED(15),
    PERFECT_QUIZ(100),
    QUIZ_COMPLETED(25),
    MOCK_EXAM_PASSED(250),
    FLASHCARD_MASTERED(5),
    PROJECT_COMPLETED(150),
    COURSE_COMPLETED(200),
    TRACK_COMPLETED(500),
    STREAK_MILESTONE_7(200),
    STREAK_MILESTONE_30(1_000),
    STREAK_MILESTONE_100(3_000),
    ACHIEVEMENT_UNLOCKED(0),
}

/** A single XP award, kept as a ledger so analytics can attribute every point. */
data class XpAward(
    val event: XpEvent,
    val amount: Int,
    val reason: String,
    val categoryId: String? = null,
    val awardedAtEpochMillis: Long,
)

/**
 * A badge definition. [criteria] is evaluated against live stats by
 * [com.aimasteracademy.app.domain.gamification.AchievementEvaluator].
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val iconRef: String,
    val tier: AchievementTier,
    val xpReward: Int,
    val criteria: AchievementCriteria,
    val isSecret: Boolean = false,
)

enum class AchievementTier(val id: String) {
    BRONZE("bronze"),
    SILVER("silver"),
    GOLD("gold"),
    PLATINUM("platinum");

    companion object {
        fun fromId(id: String): AchievementTier =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BRONZE
    }
}

/**
 * Declarative unlock conditions.
 *
 * Keeping criteria as data (rather than lambdas) means the whole achievement
 * catalogue can be seeded from JSON and later updated from the backend.
 */
sealed interface AchievementCriteria {
    data class LessonsCompleted(val count: Int) : AchievementCriteria
    data class CorrectAnswers(val count: Int) : AchievementCriteria
    data class QuestionsAnswered(val count: Int) : AchievementCriteria
    data class StreakDays(val days: Int) : AchievementCriteria
    data class TotalXp(val xp: Int) : AchievementCriteria
    data class PerfectQuizzes(val count: Int) : AchievementCriteria
    data class CourseCompleted(val courseId: String) : AchievementCriteria
    data class TrackCompleted(val trackId: String) : AchievementCriteria
    data class CategoryAccuracy(
        val category: Category,
        val minAnswered: Int,
        val minAccuracy: Float,
    ) : AchievementCriteria
    data class FlashcardsMastered(val count: Int) : AchievementCriteria
    data class DailyChallengesCompleted(val count: Int) : AchievementCriteria
    data class MockExamsPassed(val count: Int) : AchievementCriteria
}

/** An achievement the learner has actually unlocked. */
data class UnlockedAchievement(
    val achievementId: String,
    val unlockedAtEpochMillis: Long,
    val seenByUser: Boolean = false,
)

/** One row of a leaderboard. */
data class LeaderboardEntry(
    val userId: String,
    val rank: Int,
    val displayName: String,
    val avatarRef: String?,
    val xp: Int,
    val level: LearnerLevel,
    val isCurrentUser: Boolean = false,
)

enum class LeaderboardScope(val id: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ALL_TIME("all_time");

    companion object {
        fun fromId(id: String): LeaderboardScope =
            entries.firstOrNull { it.id == id } ?: WEEKLY
    }
}
