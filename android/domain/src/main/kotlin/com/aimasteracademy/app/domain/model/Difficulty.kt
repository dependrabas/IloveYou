package com.aimasteracademy.app.domain.model

/**
 * Difficulty tiers shared by lessons, questions, flashcards and AI tools.
 *
 * [weight] scales the XP awarded for a correct answer, so harder questions are
 * worth proportionally more without needing per-question tuning.
 */
enum class Difficulty(val weight: Float) {
    EASY(1.0f),
    MEDIUM(1.5f),
    HARD(2.0f),
    EXPERT(3.0f);

    companion object {
        fun fromId(id: String): Difficulty =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: MEDIUM

        /** Difficulties considered appropriate for a learner at [level]. */
        fun suitableFor(level: KnowledgeLevel): Set<Difficulty> = when (level) {
            KnowledgeLevel.BEGINNER -> setOf(EASY, MEDIUM)
            KnowledgeLevel.INTERMEDIATE -> setOf(EASY, MEDIUM, HARD)
            KnowledgeLevel.ADVANCED -> setOf(MEDIUM, HARD, EXPERT)
        }
    }
}

/** Self-declared starting point captured during onboarding personalisation. */
enum class KnowledgeLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED;

    companion object {
        fun fromId(id: String): KnowledgeLevel =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: BEGINNER
    }
}

/** Why the learner is here — drives learning-path selection and tone. */
enum class LearningMotivation {
    STUDENT,
    TEACHER,
    DEVELOPER,
    RESEARCHER,
    BUSINESS_OWNER,
    PROFESSIONAL,
    ENTHUSIAST,
    CAREER_CHANGE,
    CURIOUS;

    companion object {
        fun fromId(id: String): LearningMotivation =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: CURIOUS
    }
}

/** Minutes per day the learner committed to during personalisation. */
enum class DailyGoal(val minutes: Int, val targetXp: Int) {
    CASUAL(5, 50),
    LIGHT(10, 100),
    STEADY(15, 150),
    SERIOUS(30, 300),
    INTENSE(60, 600);

    companion object {
        fun fromMinutes(minutes: Int): DailyGoal =
            entries.minByOrNull { kotlin.math.abs(it.minutes - minutes) } ?: STEADY

        fun fromId(id: String): DailyGoal =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: STEADY
    }
}
