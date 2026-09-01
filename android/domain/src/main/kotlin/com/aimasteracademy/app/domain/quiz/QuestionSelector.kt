package com.aimasteracademy.app.domain.quiz

import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.Question
import kotlin.random.Random

/**
 * How a quiz's question set is chosen.
 *
 * @param recentlySeenIds questions answered recently. They are pushed to the
 *   back of the queue rather than removed outright, so a learner with a small
 *   pool for a niche topic still gets a full-length quiz.
 * @param seed makes selection reproducible in tests and lets a daily challenge
 *   be identical for everyone on a given day.
 */
data class SelectionSpec(
    val count: Int,
    val categories: Set<Category> = emptySet(),
    val difficulties: Set<Difficulty> = emptySet(),
    val recentlySeenIds: Set<String> = emptySet(),
    val requiredIds: List<String> = emptyList(),
    val seed: Long? = null,
)

/**
 * Builds the question list for a quiz.
 *
 * The selection rules exist to solve one problem well: a bank of thousands of
 * questions must feel varied, appropriately hard, and never repetitive.
 */
class QuestionSelector {

    /**
     * Picks up to [SelectionSpec.count] questions from [pool].
     *
     * Order of operations:
     *  1. Honour [SelectionSpec.requiredIds] verbatim (used by lesson checks and
     *     mistake review, where the question set is not negotiable).
     *  2. Filter by category and difficulty.
     *  3. Prefer unseen questions; fall back to recently-seen ones only if the
     *     filtered pool is too small.
     *  4. Spread the result across categories so a mixed quiz does not open with
     *     six questions from the same topic.
     */
    fun select(pool: List<Question>, spec: SelectionSpec): List<Question> {
        if (spec.count <= 0 || pool.isEmpty()) return emptyList()
        val random = spec.seed?.let(::Random) ?: Random.Default

        val byId = pool.associateBy(Question::id)
        val required = spec.requiredIds.mapNotNull(byId::get).take(spec.count)
        if (required.size >= spec.count) return required

        val requiredIds = required.map(Question::id).toSet()
        val candidates = pool.filter { question ->
            question.id !in requiredIds &&
                (spec.categories.isEmpty() || question.category in spec.categories) &&
                (spec.difficulties.isEmpty() || question.difficulty in spec.difficulties)
        }

        val remaining = spec.count - required.size
        val (unseen, seen) = candidates.partition { it.id !in spec.recentlySeenIds }

        val chosen = buildList {
            addAll(unseen.shuffled(random).take(remaining))
            if (size < remaining) {
                addAll(seen.shuffled(random).take(remaining - size))
            }
        }

        return required + interleaveByCategory(chosen, random)
    }

    /**
     * Round-robins across categories so consecutive questions rarely share a
     * topic, then keeps that ordering.
     */
    private fun interleaveByCategory(questions: List<Question>, random: Random): List<Question> {
        if (questions.size < 3) return questions
        val buckets = questions
            .groupBy(Question::category)
            .mapValues { (_, group) -> group.shuffled(random).toMutableList() }
            .values
            .sortedByDescending { it.size }
            .toMutableList()

        val result = ArrayList<Question>(questions.size)
        while (buckets.any { it.isNotEmpty() }) {
            for (bucket in buckets) {
                if (bucket.isNotEmpty()) result += bucket.removeAt(0)
            }
        }
        return result
    }

    /**
     * Shuffles a question's options and remaps the answer key to match.
     *
     * Returns the question unchanged for types where option order is meaningful
     * ([com.aimasteracademy.app.domain.model.QuestionType.ORDERING]) or where
     * shuffling would be confusing (true/false).
     */
    fun shuffleOptions(question: Question, random: Random = Random.Default): Question {
        if (question.options.size < 2) return question
        if (!question.type.isOptionBased) return question
        if (question.type == com.aimasteracademy.app.domain.model.QuestionType.ORDERING) return question
        if (question.type == com.aimasteracademy.app.domain.model.QuestionType.TRUE_FALSE) return question

        // The answer key stores option *text*, so a plain shuffle keeps it valid.
        return question.copy(options = question.options.shuffled(random))
    }
}
