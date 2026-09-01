package com.aimasteracademy.app.domain.gamification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreakCalculatorTest {

    private val calculator = StreakCalculator()

    @Test
    fun `first ever activity starts a one day streak`() {
        val update = calculator.recordActivity(StreakState(), todayEpochDay = 100)

        assertThat(update.outcome).isEqualTo(StreakOutcome.STARTED)
        assertThat(update.state.currentStreak).isEqualTo(1)
        assertThat(update.state.longestStreak).isEqualTo(1)
        assertThat(update.state.lastActiveEpochDay).isEqualTo(100)
    }

    @Test
    fun `studying again the same day does not double count`() {
        val state = StreakState(currentStreak = 5, longestStreak = 5, lastActiveEpochDay = 100)

        val update = calculator.recordActivity(state, todayEpochDay = 100)

        assertThat(update.outcome).isEqualTo(StreakOutcome.ALREADY_COUNTED_TODAY)
        assertThat(update.state.currentStreak).isEqualTo(5)
    }

    @Test
    fun `consecutive days extend the streak`() {
        val state = StreakState(currentStreak = 5, longestStreak = 5, lastActiveEpochDay = 100)

        val update = calculator.recordActivity(state, todayEpochDay = 101)

        assertThat(update.outcome).isEqualTo(StreakOutcome.EXTENDED)
        assertThat(update.state.currentStreak).isEqualTo(6)
        assertThat(update.state.longestStreak).isEqualTo(6)
    }

    @Test
    fun `a missed day resets the streak when no freeze is held`() {
        val state = StreakState(currentStreak = 12, longestStreak = 12, lastActiveEpochDay = 100)

        val update = calculator.recordActivity(state, todayEpochDay = 102)

        assertThat(update.outcome).isEqualTo(StreakOutcome.RESET)
        assertThat(update.state.currentStreak).isEqualTo(1)
        // The personal best survives the reset.
        assertThat(update.state.longestStreak).isEqualTo(12)
    }

    @Test
    fun `a freeze absorbs exactly one missed day`() {
        val state = StreakState(
            currentStreak = 12,
            longestStreak = 12,
            lastActiveEpochDay = 100,
            freezesRemaining = 1,
        )

        val update = calculator.recordActivity(state, todayEpochDay = 102)

        assertThat(update.outcome).isEqualTo(StreakOutcome.FREEZE_USED)
        assertThat(update.state.currentStreak).isEqualTo(13)
        assertThat(update.state.freezesRemaining).isEqualTo(0)
    }

    @Test
    fun `a freeze cannot cover a two day gap`() {
        val state = StreakState(currentStreak = 12, lastActiveEpochDay = 100, freezesRemaining = 1)

        val update = calculator.recordActivity(state, todayEpochDay = 103)

        assertThat(update.outcome).isEqualTo(StreakOutcome.RESET)
        // The freeze is not spent on a gap it could not have saved.
        assertThat(update.state.freezesRemaining).isEqualTo(1)
    }

    @Test
    fun `a backwards clock cannot inflate or destroy a streak`() {
        val state = StreakState(currentStreak = 9, longestStreak = 9, lastActiveEpochDay = 100)

        val update = calculator.recordActivity(state, todayEpochDay = 95)

        assertThat(update.outcome).isEqualTo(StreakOutcome.ALREADY_COUNTED_TODAY)
        assertThat(update.state.currentStreak).isEqualTo(9)
        assertThat(update.state.lastActiveEpochDay).isEqualTo(100)
    }

    @Test
    fun `milestones are reported on the exact day they are reached`() {
        val sixDays = StreakState(currentStreak = 6, longestStreak = 6, lastActiveEpochDay = 100)

        assertThat(calculator.recordActivity(sixDays, 101).milestoneReached).isEqualTo(7)

        val sevenDays = StreakState(currentStreak = 7, longestStreak = 7, lastActiveEpochDay = 100)
        assertThat(calculator.recordActivity(sevenDays, 101).milestoneReached).isEqualTo(0)

        val twentyNine = StreakState(currentStreak = 29, longestStreak = 29, lastActiveEpochDay = 100)
        assertThat(calculator.recordActivity(twentyNine, 101).milestoneReached).isEqualTo(30)
    }

    @Test
    fun `a thirty day run reaches both the seven and thirty day milestones once each`() {
        var state = StreakState()
        val milestones = mutableListOf<Int>()

        for (day in 1L..30L) {
            val update = calculator.recordActivity(state, day)
            state = update.state
            if (update.milestoneReached > 0) milestones += update.milestoneReached
        }

        assertThat(state.currentStreak).isEqualTo(30)
        assertThat(milestones).containsExactly(7, 30).inOrder()
    }

    @Test
    fun `displayed streak survives the day after study but not two days later`() {
        val state = StreakState(currentStreak = 8, lastActiveEpochDay = 100)

        assertThat(calculator.displayedStreak(state, 100)).isEqualTo(8)
        assertThat(calculator.displayedStreak(state, 101)).isEqualTo(8)
        assertThat(calculator.displayedStreak(state, 102)).isEqualTo(0)
    }

    @Test
    fun `streak is flagged at risk on the day after the last study day`() {
        val state = StreakState(currentStreak = 8, lastActiveEpochDay = 100)

        assertThat(calculator.isStreakAtRisk(state, 100)).isFalse()
        assertThat(calculator.isStreakAtRisk(state, 101)).isTrue()
        assertThat(calculator.isStreakAtRisk(state, 102)).isFalse()
    }
}
