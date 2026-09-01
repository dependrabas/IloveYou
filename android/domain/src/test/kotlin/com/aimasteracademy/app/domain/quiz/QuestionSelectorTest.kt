package com.aimasteracademy.app.domain.quiz

import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuestionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class QuestionSelectorTest {

    private val selector = QuestionSelector()

    private fun pool(
        size: Int,
        category: Category = Category.MACHINE_LEARNING,
        difficulty: Difficulty = Difficulty.EASY,
        idPrefix: String = "q",
    ) = (1..size).map { index ->
        Question(
            id = "$idPrefix$index",
            type = QuestionType.SINGLE_CHOICE,
            category = category,
            difficulty = difficulty,
            text = "Question $index",
            options = listOf("A", "B", "C", "D"),
            correctAnswer = listOf("A"),
            explanation = "Explanation $index",
        )
    }

    @Test
    fun `selects the requested number of questions`() {
        val selected = selector.select(pool(50), SelectionSpec(count = 10, seed = 1))

        assertThat(selected).hasSize(10)
        assertThat(selected.map(Question::id).toSet()).hasSize(10)
    }

    @Test
    fun `never returns more than the pool holds`() {
        val selected = selector.select(pool(4), SelectionSpec(count = 10, seed = 1))

        assertThat(selected).hasSize(4)
    }

    @Test
    fun `an empty pool yields an empty quiz rather than an error`() {
        assertThat(selector.select(emptyList(), SelectionSpec(count = 10))).isEmpty()
        assertThat(selector.select(pool(10), SelectionSpec(count = 0))).isEmpty()
    }

    @Test
    fun `filters by category`() {
        val mixed = pool(10, Category.MACHINE_LEARNING, idPrefix = "ml") +
            pool(10, Category.NLP, idPrefix = "nlp")

        val selected = selector.select(
            mixed,
            SelectionSpec(count = 5, categories = setOf(Category.NLP), seed = 7),
        )

        assertThat(selected).hasSize(5)
        assertThat(selected.map(Question::category).toSet()).containsExactly(Category.NLP)
    }

    @Test
    fun `filters by difficulty`() {
        val mixed = pool(10, difficulty = Difficulty.EASY, idPrefix = "e") +
            pool(10, difficulty = Difficulty.EXPERT, idPrefix = "x")

        val selected = selector.select(
            mixed,
            SelectionSpec(count = 6, difficulties = setOf(Difficulty.EXPERT), seed = 7),
        )

        assertThat(selected.map(Question::difficulty).toSet()).containsExactly(Difficulty.EXPERT)
    }

    @Test
    fun `prefers unseen questions over recently answered ones`() {
        val all = pool(20)
        val recentlySeen = all.take(15).map(Question::id).toSet()

        val selected = selector.select(
            all,
            SelectionSpec(count = 5, recentlySeenIds = recentlySeen, seed = 3),
        )

        // Five unseen questions exist, so none of the recent ones should appear.
        assertThat(selected.map(Question::id).none { it in recentlySeen }).isTrue()
    }

    @Test
    fun `falls back to seen questions when the unseen pool is too small`() {
        val all = pool(10)
        val recentlySeen = all.take(8).map(Question::id).toSet()

        val selected = selector.select(
            all,
            SelectionSpec(count = 6, recentlySeenIds = recentlySeen, seed = 3),
        )

        // A full-length quiz still gets built rather than silently shrinking.
        assertThat(selected).hasSize(6)
    }

    @Test
    fun `required ids are honoured verbatim and come first`() {
        val all = pool(30)

        val selected = selector.select(
            all,
            SelectionSpec(count = 5, requiredIds = listOf("q7", "q3"), seed = 3),
        )

        assertThat(selected).hasSize(5)
        assertThat(selected.take(2).map(Question::id)).containsExactly("q7", "q3").inOrder()
        // No duplicates once a required question is also in the general pool.
        assertThat(selected.map(Question::id).toSet()).hasSize(5)
    }

    @Test
    fun `required ids alone can fill the quiz`() {
        val all = pool(30)

        val selected = selector.select(
            all,
            SelectionSpec(count = 2, requiredIds = listOf("q7", "q3", "q9")),
        )

        assertThat(selected.map(Question::id)).containsExactly("q7", "q3").inOrder()
    }

    @Test
    fun `the same seed produces the same quiz`() {
        val all = pool(60)
        val spec = SelectionSpec(count = 10, seed = 42)

        val first = selector.select(all, spec).map(Question::id)
        val second = selector.select(all, spec).map(Question::id)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `different seeds produce different quizzes`() {
        val all = pool(200)

        val first = selector.select(all, SelectionSpec(count = 10, seed = 1)).map(Question::id)
        val second = selector.select(all, SelectionSpec(count = 10, seed = 2)).map(Question::id)

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `mixed quizzes spread consecutive questions across categories`() {
        val mixed = pool(6, Category.MACHINE_LEARNING, idPrefix = "ml") +
            pool(6, Category.NLP, idPrefix = "nlp") +
            pool(6, Category.RAG, idPrefix = "rag")

        val selected = selector.select(mixed, SelectionSpec(count = 9, seed = 11))

        val adjacentSameCategory = selected.zipWithNext().count { (a, b) -> a.category == b.category }
        // Round-robin interleaving should leave almost no same-topic neighbours.
        assertThat(adjacentSameCategory).isAtMost(1)
    }

    @Test
    fun `shuffling options keeps the answer key valid`() {
        val question = pool(1).first()

        val shuffled = selector.shuffleOptions(question, Random(5))

        assertThat(shuffled.options).containsExactlyElementsIn(question.options)
        // The key stores option text, so it must still be present after shuffling.
        assertThat(shuffled.options).containsAtLeastElementsIn(shuffled.correctAnswer)
        assertThat(AnswerGrader().score(shuffled, listOf("A"))).isEqualTo(1f)
    }

    @Test
    fun `ordering and true false questions keep their option order`() {
        val ordering = pool(1).first().copy(
            type = QuestionType.ORDERING,
            options = listOf("First", "Second", "Third"),
            correctAnswer = listOf("First", "Second", "Third"),
        )
        val trueFalse = pool(1).first().copy(
            type = QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correctAnswer = listOf("True"),
        )

        assertThat(selector.shuffleOptions(ordering, Random(1)).options)
            .containsExactly("First", "Second", "Third").inOrder()
        assertThat(selector.shuffleOptions(trueFalse, Random(1)).options)
            .containsExactly("True", "False").inOrder()
    }
}
