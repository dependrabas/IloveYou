package com.aimasteracademy.app.domain.gamification

import com.aimasteracademy.app.domain.model.LearnerLevel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LevelSystemTest {

    @Test
    fun `zero xp starts at the first rank`() {
        val progress = LevelSystem.progressFor(0)

        assertThat(progress.level).isEqualTo(LearnerLevel.AI_BEGINNER)
        assertThat(progress.xpIntoLevel).isEqualTo(0)
        assertThat(progress.fraction).isEqualTo(0f)
        assertThat(progress.isMaxLevel).isFalse()
    }

    @Test
    fun `negative xp is clamped rather than throwing`() {
        val progress = LevelSystem.progressFor(-500)

        assertThat(progress.level).isEqualTo(LearnerLevel.AI_BEGINNER)
        assertThat(progress.totalXp).isEqualTo(0)
    }

    @Test
    fun `xp exactly on a threshold promotes to that rank`() {
        val progress = LevelSystem.progressFor(LearnerLevel.AI_EXPLORER.xpThreshold)

        assertThat(progress.level).isEqualTo(LearnerLevel.AI_EXPLORER)
        assertThat(progress.xpIntoLevel).isEqualTo(0)
    }

    @Test
    fun `progress fraction is measured within the current rank only`() {
        // Halfway between AI_EXPLORER (500) and AI_LEARNER (1500).
        val progress = LevelSystem.progressFor(1_000)

        assertThat(progress.level).isEqualTo(LearnerLevel.AI_EXPLORER)
        assertThat(progress.nextLevel).isEqualTo(LearnerLevel.AI_LEARNER)
        assertThat(progress.xpIntoLevel).isEqualTo(500)
        assertThat(progress.xpRequiredForNextLevel).isEqualTo(1_000)
        assertThat(progress.fraction).isWithin(0.001f).of(0.5f)
        assertThat(progress.xpToNextLevel).isEqualTo(500)
    }

    @Test
    fun `top rank reports max level and a full bar`() {
        val progress = LevelSystem.progressFor(LearnerLevel.AI_LEGEND.xpThreshold + 5_000)

        assertThat(progress.level).isEqualTo(LearnerLevel.AI_LEGEND)
        assertThat(progress.isMaxLevel).isTrue()
        assertThat(progress.fraction).isEqualTo(1f)
        assertThat(progress.xpToNextLevel).isEqualTo(0)
    }

    @Test
    fun `crossing a threshold reports a level up`() {
        val levelUp = LevelSystem.levelUpFrom(previousXp = 480, newXp = 520)

        assertThat(levelUp).isEqualTo(LearnerLevel.AI_EXPLORER)
    }

    @Test
    fun `staying within a rank reports no level up`() {
        assertThat(LevelSystem.levelUpFrom(previousXp = 520, newXp = 700)).isNull()
    }

    @Test
    fun `a single award spanning two ranks reports the highest reached`() {
        val levelUp = LevelSystem.levelUpFrom(previousXp = 100, newXp = 1_600)

        assertThat(levelUp).isEqualTo(LearnerLevel.AI_LEARNER)
    }

    @Test
    fun `every rank is reachable and thresholds increase strictly`() {
        val thresholds = LearnerLevel.ordered.map { it.xpThreshold }

        assertThat(thresholds).isInStrictOrder()
        LearnerLevel.ordered.forEach { level ->
            assertThat(LevelSystem.progressFor(level.xpThreshold).level).isEqualTo(level)
        }
    }
}
