package com.aimasteracademy.app.domain.model

/**
 * The canonical topic taxonomy of the academy.
 *
 * Every lesson, question, flashcard and glossary term is tagged with exactly one
 * category, which is what makes cross-cutting features — weak-topic analytics,
 * topic quizzes, recommendations — possible without free-text matching.
 *
 * [id] is the stable wire identifier used in seed JSON and in the database; the
 * enum name is never persisted so the taxonomy can be reordered safely.
 */
enum class Category(val id: String) {
    AI_FUNDAMENTALS("ai_fundamentals"),
    MACHINE_LEARNING("machine_learning"),
    DEEP_LEARNING("deep_learning"),
    GENERATIVE_AI("generative_ai"),
    LLMS("llms"),
    PROMPT_ENGINEERING("prompt_engineering"),
    AI_AGENTS("ai_agents"),
    RAG("rag"),
    COMPUTER_VISION("computer_vision"),
    NLP("nlp"),
    ROBOTICS("robotics"),
    AI_ETHICS("ai_ethics"),
    AI_SECURITY("ai_security"),
    PYTHON_FOR_AI("python_for_ai"),
    DATA_SCIENCE("data_science");

    companion object {
        private val byId = entries.associateBy(Category::id)

        fun fromId(id: String): Category? = byId[id]

        fun fromIdOrDefault(id: String): Category = byId[id] ?: AI_FUNDAMENTALS
    }
}
