package com.aimasteracademy.app.domain.gamification

import com.aimasteracademy.app.domain.model.LearnerLevel
import com.aimasteracademy.app.domain.model.LevelProgress

/**
 * Translates lifetime XP into a rank and a position within that rank.
 *
 * Deliberately stateless and pure: the same XP total always yields the same
 * progress, which makes level-up detection a simple before/after comparison.
 */
object LevelSystem {

    /** Resolves [totalXp] into a full [LevelProgress] snapshot. */
    fun progressFor(totalXp: Int): LevelProgress {
        val safeXp = totalXp.coerceAtLeast(0)
        val level = LearnerLevel.forXp(safeXp)
        val next = LearnerLevel.nextAfter(level)
        val xpIntoLevel = safeXp - level.xpThreshold
        val required = next?.let { it.xpThreshold - level.xpThreshold } ?: 0
        return LevelProgress(
            level = level,
            nextLevel = next,
            totalXp = safeXp,
            xpIntoLevel = xpIntoLevel,
            xpRequiredForNextLevel = required,
        )
    }

    /**
     * Returns the rank the learner just reached if [newXp] crossed a threshold
     * that [previousXp] had not, otherwise `null`.
     *
     * The UI uses a non-null result to trigger the level-up celebration exactly
     * once, even when a single award spans two thresholds (the highest reached
     * level is returned).
     */
    fun levelUpFrom(previousXp: Int, newXp: Int): LearnerLevel? {
        val before = LearnerLevel.forXp(previousXp.coerceAtLeast(0))
        val after = LearnerLevel.forXp(newXp.coerceAtLeast(0))
        return after.takeIf { it.tier > before.tier }
    }
}
