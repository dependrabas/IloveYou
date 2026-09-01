package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.fake.FakeGamificationRepository
import com.aimasteracademy.app.domain.fake.FakeQuestionRepository
import com.aimasteracademy.app.domain.fake.FakeQuizRepository
import com.aimasteracademy.app.domain.fake.FakeUserRepository
import com.aimasteracademy.app.domain.model.Achievement
import com.aimasteracademy.app.domain.model.AchievementCriteria
import com.aimasteracademy.app.domain.model.AchievementTier
import com.aimasteracademy.app.domain.model.AnswerSubmission
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.GradedAnswer
import com.aimasteracademy.app.domain.model.LearnerLevel
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuestionType
import com.aimasteracademy.app.domain.model.QuizAttempt
import com.aimasteracademy.app.domain.model.QuizMode
import com.aimasteracademy.app.domain.model.UserStats
import com.aimasteracademy.app.domain.util.Outcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CompleteQuizUseCaseTest {

    private fun question(id: String) = Question(
        id = id,
        type = QuestionType.SINGLE_CHOICE,
        category = Category.MACHINE_LEARNING,
        difficulty = Difficulty.MEDIUM,
        text = "Question $id",
        options = listOf("A", "B"),
        correctAnswer = listOf("A"),
        explanation = "Explanation",
    )

    private fun graded(id: String, correct: Boolean) = GradedAnswer(
        questionId = id,
        submission = AnswerSubmission(id, listOf(if (correct) "A" else "B")),
        isCorrect = correct,
        isPartiallyCorrect = false,
        score = if (correct) 1f else 0f,
        xpAwarded = if (correct) 7 else 0,
        correctAnswer = listOf("A"),
        explanation = "Explanation",
    )

    private fun attempt(correct: Int, total: Int = 10, mode: QuizMode = QuizMode.QUICK) = QuizAttempt(
        id = "attempt-1",
        mode = mode,
        title = "Quick Quiz",
        category = Category.MACHINE_LEARNING,
        questionIds = (1..total).map { "q$it" },
        gradedAnswers = (1..total).map { graded("q$it", it <= correct) },
        startedAtEpochMillis = 0,
        finishedAtEpochMillis = 120_000,
        xpEarned = 0,
    )

    private fun useCase(
        questionRepo: FakeQuestionRepository = FakeQuestionRepository(),
        quizRepo: FakeQuizRepository = FakeQuizRepository(),
        gamification: FakeGamificationRepository = FakeGamificationRepository(),
        userRepo: FakeUserRepository = FakeUserRepository(),
    ) = CompleteQuizUseCase(quizRepo, gamification, questionRepo, userRepo)

    @Test
    fun `completing a quiz persists the attempt and awards xp`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val quizRepo = FakeQuizRepository()
        val gamification = FakeGamificationRepository()

        val result = useCase(questions, quizRepo, gamification).invoke(attempt(correct = 8), todayEpochDay = 100)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val data = (result as Outcome.Success).data

        assertThat(quizRepo.savedAttempts).hasSize(1)
        assertThat(data.summary.attempt.correctCount).isEqualTo(8)
        assertThat(data.xpAwarded).isGreaterThan(0)
        assertThat(gamification.totalXp.first()).isEqualTo(data.xpAwarded)
    }

    @Test
    fun `the persisted attempt records the xp that was actually earned`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val quizRepo = FakeQuizRepository()

        val result = useCase(questions, quizRepo).invoke(attempt(correct = 10), 100)
        val summary = (result as Outcome.Success).data.summary

        // The dashboard and the ledger must never disagree about a quiz's value.
        assertThat(quizRepo.savedAttempts.single().xpEarned).isEqualTo(summary.totalXp)
    }

    @Test
    fun `a storage failure aborts before any xp is awarded`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val quizRepo = FakeQuizRepository().apply { saveShouldFail = true }
        val gamification = FakeGamificationRepository()

        val result = useCase(questions, quizRepo, gamification).invoke(attempt(correct = 10), 100)

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        // No half-recorded quiz: nothing was banked.
        assertThat(gamification.totalXp.first()).isEqualTo(0)
        assertThat(gamification.awards).isEmpty()
    }

    @Test
    fun `finishing a quiz records the study day and extends the streak`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val gamification = FakeGamificationRepository()
        val subject = useCase(questions, FakeQuizRepository(), gamification)

        val day1 = (subject.invoke(attempt(correct = 5), todayEpochDay = 100) as Outcome.Success).data
        val day2 = (subject.invoke(attempt(correct = 5), todayEpochDay = 101) as Outcome.Success).data

        assertThat(day1.streakDays).isEqualTo(1)
        assertThat(day2.streakDays).isEqualTo(2)
    }

    @Test
    fun `reaching a seven day streak pays the milestone bonus once`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val gamification = FakeGamificationRepository()
        val subject = useCase(questions, FakeQuizRepository(), gamification)

        val milestoneXp = (1..8).map { day ->
            (subject.invoke(attempt(correct = 5), todayEpochDay = 99L + day) as Outcome.Success)
                .data.streakMilestoneXp
        }

        assertThat(milestoneXp.count { it > 0 }).isEqualTo(1)
        assertThat(milestoneXp[6]).isEqualTo(200)
    }

    @Test
    fun `crossing an xp threshold reports a level up`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val gamification = FakeGamificationRepository()
        val subject = useCase(questions, FakeQuizRepository(), gamification)

        // Repeat perfect quizzes until the first threshold (500 XP) is crossed.
        var levelUp: LearnerLevel? = null
        var day = 100L
        repeat(6) {
            val result = subject.invoke(attempt(correct = 10).copy(id = "a$day"), day) as Outcome.Success
            levelUp = levelUp ?: result.data.newLevel
            day++
        }

        assertThat(levelUp).isEqualTo(LearnerLevel.AI_EXPLORER)
    }

    @Test
    fun `newly satisfied achievements are unlocked and reported`() = runTest {
        val achievement = Achievement(
            id = "quiz_starter",
            title = "Quiz Starter",
            description = "Answer 10 questions",
            iconRef = "ic_badge",
            tier = AchievementTier.BRONZE,
            xpReward = 50,
            criteria = AchievementCriteria.QuestionsAnswered(10),
        )
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val gamification = FakeGamificationRepository(catalogue = listOf(achievement))
        val userRepo = FakeUserRepository(UserStats(questionsAnswered = 12, correctAnswers = 9))

        val result = useCase(questions, FakeQuizRepository(), gamification, userRepo)
            .invoke(attempt(correct = 9), 100) as Outcome.Success

        assertThat(result.data.unlockedAchievements.map { it.id }).containsExactly("quiz_starter")
        assertThat(gamification.unlockedAchievements.first()).hasSize(1)
    }

    @Test
    fun `an already unlocked achievement is not reported twice`() = runTest {
        val achievement = Achievement(
            id = "quiz_starter",
            title = "Quiz Starter",
            description = "",
            iconRef = "ic_badge",
            tier = AchievementTier.BRONZE,
            xpReward = 50,
            criteria = AchievementCriteria.QuestionsAnswered(10),
        )
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val gamification = FakeGamificationRepository(catalogue = listOf(achievement))
        val userRepo = FakeUserRepository(UserStats(questionsAnswered = 50))
        val subject = useCase(questions, FakeQuizRepository(), gamification, userRepo)

        subject.invoke(attempt(correct = 9), 100)
        val second = subject.invoke(attempt(correct = 9).copy(id = "attempt-2"), 101) as Outcome.Success

        assertThat(second.data.unlockedAchievements).isEmpty()
    }

    @Test
    fun `the daily challenge is credited as a daily challenge award`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..10).map { question("q$it") }.toTypedArray())
        }
        val gamification = FakeGamificationRepository()

        useCase(questions, FakeQuizRepository(), gamification)
            .invoke(attempt(correct = 10, mode = QuizMode.DAILY_CHALLENGE), 100)

        assertThat(gamification.awards.first().event.name).isEqualTo("DAILY_CHALLENGE_COMPLETED")
    }
}
