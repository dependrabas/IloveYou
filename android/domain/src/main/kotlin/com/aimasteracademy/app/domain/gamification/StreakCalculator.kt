package com.aimasteracademy.app.domain.gamification

/**
 * The learner's streak state, in whole days.
 *
 * @param freezesRemaining unused "streak freezes"; one is spent automatically to
 *   absorb a single missed day rather than resetting a long streak to zero.
 */
data class StreakState(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActiveEpochDay: Long? = null,
    val freezesRemaining: Int = 0,
)

/** What happened to the streak when activity was recorded. */
enum class StreakOutcome {
    /** Activity on a day that was already counted — nothing changed. */
    ALREADY_COUNTED_TODAY,

    /** The very first active day. */
    STARTED,

    /** Continued from yesterday. */
    EXTENDED,

    /** A gap was absorbed by spending a freeze. */
    FREEZE_USED,

    /** The gap was too large; the streak restarted at 1. */
    RESET,
}

data class StreakUpdate(
    val state: StreakState,
    val outcome: StreakOutcome,
    /** Non-zero on the day a 7/30/100-day milestone is reached. */
    val milestoneReached: Int = 0,
)

/**
 * Pure day-arithmetic over learning streaks.
 *
 * Working in epoch *days* rather than timestamps keeps the rules independent of
 * clocks and time zones — the caller decides which local day an event belongs
 * to, and the calculator only ever compares day numbers.
 */
class StreakCalculator {

    /**
     * Records activity on [todayEpochDay] and returns the updated streak.
     *
     * Days in the past relative to [StreakState.lastActiveEpochDay] are ignored:
     * a clock that jumps backwards must never inflate or destroy a streak.
     */
    fun recordActivity(state: StreakState, todayEpochDay: Long): StreakUpdate {
        val last = state.lastActiveEpochDay
            ?: return started(todayEpochDay, state.freezesRemaining)

        val gap = todayEpochDay - last

        return when {
            gap <= 0L -> StreakUpdate(state, StreakOutcome.ALREADY_COUNTED_TODAY)

            gap == 1L -> extend(state, todayEpochDay, StreakOutcome.EXTENDED)

            // Exactly one missed day can be covered by a freeze.
            gap == 2L && state.freezesRemaining > 0 ->
                extend(
                    state.copy(freezesRemaining = state.freezesRemaining - 1),
                    todayEpochDay,
                    StreakOutcome.FREEZE_USED,
                )

            else -> StreakUpdate(
                state = state.copy(
                    currentStreak = 1,
                    longestStreak = maxOf(state.longestStreak, 1),
                    lastActiveEpochDay = todayEpochDay,
                ),
                outcome = StreakOutcome.RESET,
            )
        }
    }

    /**
     * The streak as it should be *displayed* on [todayEpochDay], without
     * recording activity.
     *
     * A streak stays alive on the day after the last active day (the learner
     * still has today to study); beyond that it reads as broken.
     */
    fun displayedStreak(state: StreakState, todayEpochDay: Long): Int {
        val last = state.lastActiveEpochDay ?: return 0
        val gap = todayEpochDay - last
        return when {
            gap <= 1L -> state.currentStreak
            gap == 2L && state.freezesRemaining > 0 -> state.currentStreak
            else -> 0
        }
    }

    /** True when studying today would keep the streak alive rather than restart it. */
    fun isStreakAtRisk(state: StreakState, todayEpochDay: Long): Boolean {
        val last = state.lastActiveEpochDay ?: return false
        return todayEpochDay - last == 1L && state.currentStreak > 0
    }

    private fun started(today: Long, freezes: Int) = StreakUpdate(
        state = StreakState(
            currentStreak = 1,
            longestStreak = 1,
            lastActiveEpochDay = today,
            freezesRemaining = freezes,
        ),
        outcome = StreakOutcome.STARTED,
    )

    private fun extend(state: StreakState, today: Long, outcome: StreakOutcome): StreakUpdate {
        val newStreak = state.currentStreak + 1
        return StreakUpdate(
            state = state.copy(
                currentStreak = newStreak,
                longestStreak = maxOf(state.longestStreak, newStreak),
                lastActiveEpochDay = today,
            ),
            outcome = outcome,
            milestoneReached = MILESTONES.firstOrNull { it == newStreak } ?: 0,
        )
    }

    private companion object {
        val MILESTONES = intArrayOf(7, 30, 100)
    }
}
