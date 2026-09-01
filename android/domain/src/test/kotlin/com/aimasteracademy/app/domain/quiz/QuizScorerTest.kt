package com.aimasteracademy.app.domain.quiz

import com.aimasteracademy.app.domain.model.AnswerSubmission
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.GradedAnswer
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuestionType
import com.aimasteracademy.app.domain.model.QuizAttempt
import com.aimasteracademy.app.domain.model.QuizMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuizScorerTest {

    private val scorer = QuizScorer()

    private fun question(id: String, category: Category) = Question(
        id = id,
        type = QuestionType.SINGLE_CHOICE,
        category = category,
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

    private fun attempt(
        questions: List<Question>,
        answers: List<GradedAnswer>,
        mode: QuizMode = QuizMode.QUICK,
    ) = QuizAttempt(
        id = "attempt1",
        mode = mode,
        title = "Quick Quiz",
        category = null,
        questionIds = questions.map(Question::id),
        gradedAnswers = answers,
        startedAtEpochMillis = 0L,
        finishedAtEpochMillis = 225_000L,
        xpEarned = 0,
    )

    @Test
    fun `attempt totals count correct incorrect and skipped`() {
        val questions = (1..10).map { question("q$it", Category.MACHINE_LEARNING) }
        val answers = (1..8).map { graded("q$it", correct = it <= 6) }

        val a = attempt(questions, answers)

        assertThat(a.correctCount).isEqualTo(6)
        assertThat(a.incorrectCount).isEqualTo(2)
        assertThat(a.skippedCount).isEqualTo(2)
        // Accuracy is measured against the whole paper, so skipping is not free.
        assertThat(a.accuracyPercent).isEqualTo(60)
        assertThat(a.durationMillis).isEqualTo(225_000L)
    }

    @Test
    fun `a perfect attempt is flagged and celebrated`() {
        val questions = (1..5).map { question("q$it", Category.NLP) }
        val a = attempt(questions, questions.map { graded(it.id, correct = true) })

        val summary = scorer.summarise(a, questions)

        assertThat(a.isPerfect).isTrue()
        assertThat(summary.band).isEqualTo(ScoreBand.PERFECT)
        assertThat(summary.band.deservesCelebration).isTrue()
    }

    @Test
    fun `score bands map to the expected thresholds`() {
        assertThat(ScoreBand.forPercent(100)).isEqualTo(ScoreBand.PERFECT)
        assertThat(ScoreBand.forPercent(80)).isEqualTo(ScoreBand.EXCELLENT)
        assertThat(ScoreBand.forPercent(79)).isEqualTo(ScoreBand.GOOD)
        assertThat(ScoreBand.forPercent(60)).isEqualTo(ScoreBand.GOOD)
        assertThat(ScoreBand.forPercent(41)).isEqualTo(ScoreBand.FAIR)
        assertThat(ScoreBand.forPercent(0)).isEqualTo(ScoreBand.NEEDS_WORK)
        // A mediocre score does not get confetti.
        assertThat(ScoreBand.forPercent(65).deservesCelebration).isFalse()
    }

    @Test
    fun `a perfect quiz earns more completion xp than a merely good one`() {
        val questions = (1..5).map { question("q$it", Category.NLP) }
        val perfect = attempt(questions, questions.map { graded(it.id, true) })
        val good = attempt(questions, questions.mapIndexed { i, q -> graded(q.id, i < 4) })

        assertThat(scorer.summarise(perfect, questions).completionXp)
            .isGreaterThan(scorer.summarise(good, questions).completionXp)
    }

    @Test
    fun `the daily challenge pays a larger completion bonus than a quick quiz`() {
        val questions = (1..10).map { question("q$it", Category.LLMS) }
        val answers = questions.map { graded(it.id, true) }

        val daily = scorer.summarise(attempt(questions, answers, QuizMode.DAILY_CHALLENGE), questions)
        val quick = scorer.summarise(attempt(questions, answers, QuizMode.QUICK), questions)

        assertThat(daily.completionXp).isGreaterThan(quick.completionXp)
    }

    @Test
    fun `topic breakdown reports accuracy per category weakest first`() {
        val questions = listOf(
            question("q1", Category.NLP),
            question("q2", Category.NLP),
            question("q3", Category.DEEP_LEARNING),
            question("q4", Category.DEEP_LEARNING),
        )
        val answers = listOf(
            graded("q1", true), graded("q2", true),
            graded("q3", false), graded("q4", false),
        )

        val breakdown = scorer.topicBreakdown(attempt(questions, answers), questions)

        assertThat(breakdown.map { it.category })
            .containsExactly(Category.DEEP_LEARNING, Category.NLP).inOrder()
        assertThat(breakdown.first().accuracy).isEqualTo(0f)
        assertThat(breakdown.last().accuracy).isEqualTo(1f)
    }

    @Test
    fun `weak categories need at least two answers to be judged`() {
        val questions = listOf(
            question("q1", Category.RAG),
            question("q2", Category.NLP),
            question("q3", Category.NLP),
        )
        // One wrong RAG answer, two wrong NLP answers.
        val answers = listOf(graded("q1", false), graded("q2", false), graded("q3", false))

        val weak = scorer.weakCategories(scorer.topicBreakdown(attempt(questions, answers), questions))

        // A single unlucky miss does not brand RAG as a weak topic.
        assertThat(weak).containsExactly(Category.NLP)
    }

    @Test
    fun `an empty attempt summarises without dividing by zero`() {
        val a = attempt(emptyList(), emptyList())

        val summary = scorer.summarise(a, emptyList())

        assertThat(a.accuracy).isEqualTo(0f)
        assertThat(summary.completionXp).isEqualTo(0)
        assertThat(summary.topicBreakdown).isEmpty()
    }
}
