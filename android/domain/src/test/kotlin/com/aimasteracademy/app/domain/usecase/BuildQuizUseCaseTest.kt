package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.fake.FakeQuestionRepository
import com.aimasteracademy.app.domain.fake.FakeQuizRepository
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuestionType
import com.aimasteracademy.app.domain.model.QuizMode
import com.aimasteracademy.app.domain.util.DomainError
import com.aimasteracademy.app.domain.util.Outcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BuildQuizUseCaseTest {

    private fun question(id: String, category: Category = Category.MACHINE_LEARNING) = Question(
        id = id,
        type = QuestionType.SINGLE_CHOICE,
        category = category,
        difficulty = Difficulty.MEDIUM,
        text = "Question $id",
        options = listOf("A", "B", "C", "D"),
        correctAnswer = listOf("A"),
        explanation = "Explanation",
    )

    @Test
    fun `builds a quiz of the requested length`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..50).map { question("q$it") }.toTypedArray())
        }

        val result = BuildQuizUseCase(questions, FakeQuizRepository())
            .invoke(QuizMode.QUICK, count = 10) as Outcome.Success

        assertThat(result.data).hasSize(10)
    }

    @Test
    fun `an empty question bank reports content unavailable`() = runTest {
        val result = BuildQuizUseCase(FakeQuestionRepository(), FakeQuizRepository())
            .invoke(QuizMode.QUICK, count = 10)

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.ContentUnavailable)
    }

    @Test
    fun `mistake review with nothing to review is an empty quiz not an error`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..20).map { question("q$it") }.toTypedArray())
            incorrectIds = emptyList()
        }

        val result = BuildQuizUseCase(questions, FakeQuizRepository())
            .invoke(QuizMode.MISTAKE_REVIEW, count = 10)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).data).isEmpty()
    }

    @Test
    fun `mistake review draws only from previously missed questions`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..20).map { question("q$it") }.toTypedArray())
            incorrectIds = listOf("q3", "q7", "q11")
        }

        val result = BuildQuizUseCase(questions, FakeQuizRepository())
            .invoke(QuizMode.MISTAKE_REVIEW, count = 3) as Outcome.Success

        assertThat(result.data.map(Question::id)).containsExactly("q3", "q7", "q11")
    }

    @Test
    fun `a topic quiz only contains questions from that topic`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..15).map { question("ml$it", Category.MACHINE_LEARNING) }.toTypedArray())
            seed(*(1..15).map { question("nlp$it", Category.NLP) }.toTypedArray())
        }

        val result = BuildQuizUseCase(questions, FakeQuizRepository())
            .invoke(QuizMode.TOPIC, count = 8, categories = setOf(Category.NLP)) as Outcome.Success

        assertThat(result.data.map(Question::category).toSet()).containsExactly(Category.NLP)
    }

    @Test
    fun `the daily challenge is identical for the same day and differs across days`() = runTest {
        val questions = FakeQuestionRepository().apply {
            seed(*(1..200).map { question("q$it") }.toTypedArray())
        }
        val subject = BuildQuizUseCase(questions, FakeQuizRepository())

        val todayA = (subject.dailyChallenge(20_000) as Outcome.Success).data.map(Question::id)
        val todayB = (subject.dailyChallenge(20_000) as Outcome.Success).data.map(Question::id)
        val tomorrow = (subject.dailyChallenge(20_001) as Outcome.Success).data.map(Question::id)

        assertThat(todayA).isEqualTo(todayB)
        assertThat(todayA).isNotEqualTo(tomorrow)
        assertThat(todayA).hasSize(10)
    }
}
