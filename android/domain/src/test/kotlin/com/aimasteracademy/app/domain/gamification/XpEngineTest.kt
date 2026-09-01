package com.aimasteracademy.app.domain.gamification

import com.aimasteracademy.app.domain.model.AnswerSubmission
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.GradedAnswer
import com.aimasteracademy.app.domain.model.QuizAttempt
import com.aimasteracademy.app.domain.model.QuizMode
import com.aimasteracademy.app.domain.model.XpEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XpEngineTest {

    private val engine = XpEngine(clock = { 1_700_000_000_000L })

    private fun attempt(total: Int, correct: Int, mode: QuizMode = QuizMode.QUICK): QuizAttempt {
        val ids = (1..total).map { "q$it" }
        return QuizAttempt(
            id = "a",
            mode = mode,
            title = "Quiz",
            category = Category.MACHINE_LEARNING,
            questionIds = ids,
            gradedAnswers = ids.mapIndexed { index, id ->
                GradedAnswer(
                    questionId = id,
                    submission = AnswerSubmission(id, listOf("A")),
                    isCorrect = index < correct,
                    isPartiallyCorrect = false,
                    score = if (index < correct) 1f else 0f,
                    xpAwarded = 0,
                    correctAnswer = listOf("A"),
                    explanation = "",
                )
            },
            startedAtEpochMillis = 0,
            finishedAtEpochMillis = 1_000,
            xpEarned = 0,
        )
    }

    @Test
    fun `correct answer xp scales with difficulty`() {
        val easy = engine.forCorrectAnswer(Difficulty.EASY)
        val medium = engine.forCorrectAnswer(Difficulty.MEDIUM)
        val hard = engine.forCorrectAnswer(Difficulty.HARD)
        val expert = engine.forCorrectAnswer(Difficulty.EXPERT)

        assertThat(easy).isEqualTo(XpEvent.CORRECT_ANSWER.baseXp)
        assertThat(listOf(easy, medium, hard, expert)).isInStrictOrder()
    }

    @Test
    fun `a hint reduces the reward but always leaves at least one point`() {
        val withHint = engine.forCorrectAnswer(Difficulty.EASY, usedHint = true)

        assertThat(withHint).isLessThan(engine.forCorrectAnswer(Difficulty.EASY))
        assertThat(withHint).isAtLeast(1)
    }

    @Test
    fun `longer lessons are worth more but the bonus is capped`() {
        val short = engine.forLessonCompleted(3)
        val medium = engine.forLessonCompleted(12)
        val veryLong = engine.forLessonCompleted(120)

        assertThat(short).isEqualTo(XpEvent.LESSON_COMPLETED.baseXp)
        assertThat(medium).isGreaterThan(short)
        assertThat(veryLong).isEqualTo(XpEvent.LESSON_COMPLETED.baseXp + 20)
    }

    @Test
    fun `quiz completion xp rises with accuracy`() {
        val poor = engine.forQuizCompletion(attempt(10, 3))
        val good = engine.forQuizCompletion(attempt(10, 8))

        assertThat(good).isGreaterThan(poor)
    }

    @Test
    fun `a perfect quiz earns the perfect bonus`() {
        val perfect = engine.forQuizCompletion(attempt(10, 10))
        val nearlyPerfect = engine.forQuizCompletion(attempt(10, 9))

        assertThat(perfect - nearlyPerfect).isAtLeast(XpEvent.PERFECT_QUIZ.baseXp)
    }

    @Test
    fun `an empty quiz awards nothing`() {
        assertThat(engine.forQuizCompletion(attempt(0, 0))).isEqualTo(0)
    }

    @Test
    fun `failing an exam awards no xp and passing scales with the margin`() {
        assertThat(engine.forExamPassed(scorePercent = 65, passMarkPercent = 70)).isEqualTo(0)

        val justPassed = engine.forExamPassed(70, 70)
        val aced = engine.forExamPassed(100, 70)

        assertThat(justPassed).isEqualTo(XpEvent.MOCK_EXAM_PASSED.baseXp)
        assertThat(aced).isGreaterThan(justPassed)
    }

    @Test
    fun `streak milestones pay out only on milestone days`() {
        assertThat(engine.forStreakMilestone(6)).isEqualTo(0)
        assertThat(engine.forStreakMilestone(7)).isEqualTo(XpEvent.STREAK_MILESTONE_7.baseXp)
        assertThat(engine.forStreakMilestone(8)).isEqualTo(0)
        assertThat(engine.forStreakMilestone(30)).isEqualTo(XpEvent.STREAK_MILESTONE_30.baseXp)
        assertThat(engine.forStreakMilestone(100)).isEqualTo(XpEvent.STREAK_MILESTONE_100.baseXp)
    }

    @Test
    fun `awards are timestamped from the injected clock`() {
        val award = engine.award(XpEvent.LESSON_COMPLETED, 20, "Intro to AI", "ai_fundamentals")

        assertThat(award.awardedAtEpochMillis).isEqualTo(1_700_000_000_000L)
        assertThat(award.amount).isEqualTo(20)
        assertThat(award.categoryId).isEqualTo("ai_fundamentals")
    }
}
