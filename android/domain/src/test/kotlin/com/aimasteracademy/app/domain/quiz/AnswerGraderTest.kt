package com.aimasteracademy.app.domain.quiz

import com.aimasteracademy.app.domain.model.AnswerSubmission
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuestionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnswerGraderTest {

    private val grader = AnswerGrader()

    private fun question(
        type: QuestionType,
        options: List<String> = emptyList(),
        correct: List<String>,
        difficulty: Difficulty = Difficulty.EASY,
    ) = Question(
        id = "q1",
        type = type,
        category = Category.MACHINE_LEARNING,
        difficulty = difficulty,
        text = "Sample question",
        options = options,
        correctAnswer = correct,
        explanation = "Because that is how it works.",
    )

    private fun submit(vararg response: String) = AnswerSubmission("q1", response.toList())

    @Test
    fun `single choice marks the right option correct`() {
        val q = question(
            QuestionType.SINGLE_CHOICE,
            options = listOf("Supervised", "Unsupervised"),
            correct = listOf("Supervised"),
        )

        val graded = grader.grade(q, submit("Supervised"))

        assertThat(graded.isCorrect).isTrue()
        assertThat(graded.score).isEqualTo(1f)
        assertThat(graded.xpAwarded).isGreaterThan(0)
    }

    @Test
    fun `an incorrect answer still carries the correct answer and explanation`() {
        val q = question(
            QuestionType.SINGLE_CHOICE,
            options = listOf("Supervised", "Unsupervised"),
            correct = listOf("Supervised"),
        )

        val graded = grader.grade(q, submit("Unsupervised"))

        assertThat(graded.isCorrect).isFalse()
        assertThat(graded.xpAwarded).isEqualTo(0)
        // The teaching contract: never only say "wrong".
        assertThat(graded.correctAnswer).containsExactly("Supervised")
        assertThat(graded.explanation).isNotEmpty()
    }

    @Test
    fun `an empty submission scores zero instead of throwing`() {
        val q = question(QuestionType.SINGLE_CHOICE, correct = listOf("A"))

        val graded = grader.grade(q, AnswerSubmission("q1", emptyList()))

        assertThat(graded.isCorrect).isFalse()
        assertThat(graded.score).isEqualTo(0f)
    }

    @Test
    fun `multiple answer requires the exact set regardless of order`() {
        val q = question(
            QuestionType.MULTIPLE_ANSWER,
            options = listOf("Weights", "Biases", "Epochs", "Pixels"),
            correct = listOf("Weights", "Biases"),
        )

        assertThat(grader.score(q, listOf("Biases", "Weights"))).isEqualTo(1f)
        // Missing one selection is not partial credit for this type.
        assertThat(grader.score(q, listOf("Weights"))).isEqualTo(0f)
        // An extra wrong selection invalidates the answer.
        assertThat(grader.score(q, listOf("Weights", "Biases", "Epochs"))).isEqualTo(0f)
    }

    @Test
    fun `true false grading works both ways`() {
        val q = question(
            QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correct = listOf("False"),
        )

        assertThat(grader.score(q, listOf("False"))).isEqualTo(1f)
        assertThat(grader.score(q, listOf("True"))).isEqualTo(0f)
    }

    @Test
    fun `fill in the blank ignores case whitespace and trailing punctuation`() {
        val q = question(QuestionType.FILL_IN_BLANK, correct = listOf("supervised learning"))

        assertThat(grader.score(q, listOf("Supervised Learning"))).isEqualTo(1f)
        assertThat(grader.score(q, listOf("  supervised   learning  "))).isEqualTo(1f)
        assertThat(grader.score(q, listOf("Supervised learning."))).isEqualTo(1f)
        assertThat(grader.score(q, listOf("unsupervised learning"))).isEqualTo(0f)
        assertThat(grader.score(q, listOf("   "))).isEqualTo(0f)
    }

    @Test
    fun `fill in the blank accepts any listed synonym`() {
        val q = question(QuestionType.FILL_IN_BLANK, correct = listOf("overfitting", "over-fitting"))

        assertThat(grader.score(q, listOf("Over-Fitting"))).isEqualTo(1f)
        assertThat(grader.score(q, listOf("overfitting"))).isEqualTo(1f)
    }

    @Test
    fun `matching awards proportional partial credit`() {
        val q = question(
            QuestionType.MATCHING,
            correct = listOf("CNN|Images", "RNN|Sequences", "GAN|Generation", "SVM|Classification"),
        )

        val graded = grader.grade(
            q,
            AnswerSubmission("q1", listOf("CNN|Images", "RNN|Sequences", "GAN|Classification", "SVM|Generation")),
        )

        assertThat(graded.score).isWithin(0.001f).of(0.5f)
        assertThat(graded.isCorrect).isFalse()
        assertThat(graded.isPartiallyCorrect).isTrue()
        assertThat(graded.xpAwarded).isGreaterThan(0)
    }

    @Test
    fun `a fully correct match is not flagged as partial`() {
        val q = question(QuestionType.MATCHING, correct = listOf("CNN|Images", "RNN|Sequences"))

        val graded = grader.grade(q, AnswerSubmission("q1", listOf("RNN|Sequences", "CNN|Images")))

        assertThat(graded.isCorrect).isTrue()
        assertThat(graded.isPartiallyCorrect).isFalse()
    }

    @Test
    fun `ordering awards credit per correctly placed item`() {
        val q = question(
            QuestionType.ORDERING,
            correct = listOf("Chunk", "Embed", "Retrieve", "Generate"),
        )

        assertThat(grader.score(q, listOf("Chunk", "Embed", "Retrieve", "Generate"))).isEqualTo(1f)
        // First two right, last two swapped.
        assertThat(grader.score(q, listOf("Chunk", "Embed", "Generate", "Retrieve")))
            .isWithin(0.001f).of(0.5f)
        assertThat(grader.score(q, listOf("Generate", "Retrieve", "Embed", "Chunk"))).isEqualTo(0f)
    }

    @Test
    fun `a short ordering response still earns credit for what it got right`() {
        val q = question(QuestionType.ORDERING, correct = listOf("A", "B", "C", "D"))

        assertThat(grader.score(q, listOf("A", "B"))).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `harder questions are worth more xp`() {
        val easy = question(QuestionType.SINGLE_CHOICE, correct = listOf("A"), difficulty = Difficulty.EASY)
        val expert = question(QuestionType.SINGLE_CHOICE, correct = listOf("A"), difficulty = Difficulty.EXPERT)

        val easyXp = grader.grade(easy, submit("A")).xpAwarded
        val expertXp = grader.grade(expert, submit("A")).xpAwarded

        assertThat(expertXp).isGreaterThan(easyXp)
    }

    @Test
    fun `using a hint reduces but does not eliminate the reward`() {
        val q = question(QuestionType.SINGLE_CHOICE, correct = listOf("A"), difficulty = Difficulty.HARD)

        val withHint = grader.grade(q, AnswerSubmission("q1", listOf("A"), usedHint = true)).xpAwarded
        val without = grader.grade(q, AnswerSubmission("q1", listOf("A"), usedHint = false)).xpAwarded

        assertThat(withHint).isLessThan(without)
        assertThat(withHint).isGreaterThan(0)
    }

    @Test
    fun `code and scenario questions grade like single choice`() {
        val code = question(
            QuestionType.CODE,
            options = listOf("model.fit(X, y)", "model.predict(X)"),
            correct = listOf("model.fit(X, y)"),
        )
        val scenario = question(
            QuestionType.SCENARIO,
            options = listOf("Collect more data", "Add more layers"),
            correct = listOf("Collect more data"),
        )

        assertThat(grader.score(code, listOf("model.fit(X, y)"))).isEqualTo(1f)
        assertThat(grader.score(scenario, listOf("Add more layers"))).isEqualTo(0f)
    }
}
