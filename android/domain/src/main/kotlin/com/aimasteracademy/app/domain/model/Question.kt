package com.aimasteracademy.app.domain.model

/**
 * The nine question formats the quiz engine supports.
 *
 * Each format decides two things: how the question is rendered, and how
 * [com.aimasteracademy.app.domain.quiz.AnswerGrader] compares a submission to
 * [Question.correctAnswer].
 */
enum class QuestionType(val id: String) {
    /** Exactly one correct option. */
    SINGLE_CHOICE("single_choice"),

    /** Two or more correct options; order-insensitive, all-or-nothing by default. */
    MULTIPLE_ANSWER("multiple_answer"),

    /** A two-option specialisation of [SINGLE_CHOICE]. */
    TRUE_FALSE("true_false"),

    /** Free text compared case- and whitespace-insensitively against accepted answers. */
    FILL_IN_BLANK("fill_in_blank"),

    /** Left-hand prompts paired with right-hand answers. */
    MATCHING("matching"),

    /** Options arranged into the correct sequence. */
    ORDERING("ordering"),

    /** Single choice, rendered with a supporting image. */
    IMAGE_CHOICE("image_choice"),

    /** Single choice over code snippets, rendered with syntax highlighting. */
    CODE("code"),

    /** Single choice framed as an applied real-world situation. */
    SCENARIO("scenario");

    /** True when the learner picks from a fixed option list rather than typing. */
    val isOptionBased: Boolean
        get() = this != FILL_IN_BLANK

    companion object {
        private val byId = entries.associateBy(QuestionType::id)
        fun fromId(id: String): QuestionType = byId[id] ?: SINGLE_CHOICE
    }
}

/**
 * A single assessable item.
 *
 * @param correctAnswer the canonical answer key. Its interpretation depends on
 *   [type]: option indices for choice types, accepted strings for
 *   [QuestionType.FILL_IN_BLANK], `"left|right"` pairs for
 *   [QuestionType.MATCHING], and the ordered option indices for
 *   [QuestionType.ORDERING].
 * @param explanation shown after *every* submission, right or wrong. The academy
 *   never says only "incorrect" — the explanation is the teaching moment.
 * @param points base XP before difficulty weighting is applied.
 */
data class Question(
    val id: String,
    val type: QuestionType,
    val category: Category,
    val difficulty: Difficulty,
    val text: String,
    val options: List<String> = emptyList(),
    val correctAnswer: List<String>,
    val explanation: String,
    val hint: String? = null,
    val points: Int = 10,
    val topicTags: List<String> = emptyList(),
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val imageRef: String? = null,
    val relatedLessonId: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Question id must not be blank" }
        require(correctAnswer.isNotEmpty()) { "Question $id has no correct answer" }
    }

    /** XP awarded for answering this question correctly. */
    val xpValue: Int get() = (points * difficulty.weight).toInt()
}

/** A learner's submission for one [Question] inside an attempt. */
data class AnswerSubmission(
    val questionId: String,
    val response: List<String>,
    val timeSpentMillis: Long = 0L,
    val usedHint: Boolean = false,
)

/** The graded outcome of a single [AnswerSubmission]. */
data class GradedAnswer(
    val questionId: String,
    val submission: AnswerSubmission,
    val isCorrect: Boolean,
    val isPartiallyCorrect: Boolean,
    /** 0f..1f — used by partial-credit modes such as matching and ordering. */
    val score: Float,
    val xpAwarded: Int,
    val correctAnswer: List<String>,
    val explanation: String,
)
