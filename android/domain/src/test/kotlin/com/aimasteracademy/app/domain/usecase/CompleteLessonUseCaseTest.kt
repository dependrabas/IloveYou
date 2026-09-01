package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.fake.FakeContentRepository
import com.aimasteracademy.app.domain.fake.FakeGamificationRepository
import com.aimasteracademy.app.domain.fake.FakeProgressRepository
import com.aimasteracademy.app.domain.fake.FakeUserRepository
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.Lesson
import com.aimasteracademy.app.domain.model.ProgressStatus
import com.aimasteracademy.app.domain.util.Outcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CompleteLessonUseCaseTest {

    private fun lesson(id: String, minutes: Int = 10) = Lesson(
        id = id,
        moduleId = "m1",
        courseId = "c1",
        trackId = "t1",
        title = "Lesson $id",
        subtitle = "",
        category = Category.AI_FUNDAMENTALS,
        difficulty = Difficulty.EASY,
        estimatedMinutes = minutes,
        blocks = emptyList(),
        summaryPoints = emptyList(),
        quizQuestionIds = emptyList(),
        order = 0,
    )

    @Test
    fun `completing a lesson awards xp and marks it complete`() = runTest {
        val progress = FakeProgressRepository()
        val gamification = FakeGamificationRepository()
        val content = FakeContentRepository().apply { seedLessons(lesson("l1")) }
        val userRepo = FakeUserRepository()

        val result = CompleteLessonUseCase(progress, gamification, content, userRepo)
            .invoke(lesson("l1"), todayEpochDay = 100) as Outcome.Success

        assertThat(result.data.xpAwarded).isGreaterThan(0)
        assertThat(progress.getLessonStatus("l1")).isEqualTo(ProgressStatus.COMPLETED)
        assertThat(userRepo.recordedStudyMinutes).isEqualTo(10)
    }

    @Test
    fun `re-completing a lesson does not award xp twice`() = runTest {
        val progress = FakeProgressRepository()
        val gamification = FakeGamificationRepository()
        val content = FakeContentRepository().apply { seedLessons(lesson("l1")) }
        val subject = CompleteLessonUseCase(progress, gamification, content, FakeUserRepository())

        subject.invoke(lesson("l1"), 100)
        val xpAfterFirst = gamification.totalXp.first()

        val second = subject.invoke(lesson("l1"), 101) as Outcome.Success

        assertThat(second.data.xpAwarded).isEqualTo(0)
        assertThat(gamification.totalXp.first()).isEqualTo(xpAfterFirst)
    }

    @Test
    fun `a storage failure awards nothing`() = runTest {
        val progress = FakeProgressRepository().apply { completeShouldFail = true }
        val gamification = FakeGamificationRepository()
        val content = FakeContentRepository().apply { seedLessons(lesson("l1")) }

        val result = CompleteLessonUseCase(progress, gamification, content, FakeUserRepository())
            .invoke(lesson("l1"), 100)

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(gamification.totalXp.first()).isEqualTo(0)
    }

    @Test
    fun `finishing the last lesson of a course pays the course bonus`() = runTest {
        val progress = FakeProgressRepository()
        val gamification = FakeGamificationRepository()
        val content = FakeContentRepository().apply {
            seedLessons(lesson("l1"), lesson("l2"))
        }
        val subject = CompleteLessonUseCase(progress, gamification, content, FakeUserRepository())

        val first = subject.invoke(lesson("l1"), 100) as Outcome.Success
        assertThat(first.data.courseCompleted).isFalse()

        val second = subject.invoke(lesson("l2"), 101) as Outcome.Success

        assertThat(second.data.courseCompleted).isTrue()
        assertThat(gamification.awards.map { it.event.name }).contains("COURSE_COMPLETED")
    }

    @Test
    fun `longer lessons award more xp than short ones`() = runTest {
        suspend fun xpFor(minutes: Int): Int {
            val gamification = FakeGamificationRepository()
            val content = FakeContentRepository().apply { seedLessons(lesson("l1", minutes)) }
            CompleteLessonUseCase(FakeProgressRepository(), gamification, content, FakeUserRepository())
                .invoke(lesson("l1", minutes), 100)
            return gamification.awards.first().amount
        }

        assertThat(xpFor(20)).isGreaterThan(xpFor(3))
    }
}
