package com.aimasteracademy.app.domain.model

/**
 * A two-sided study card.
 *
 * Scheduling state lives in [FlashcardReview] rather than here so that the card
 * content stays immutable and shareable between learners.
 */
data class Flashcard(
    val id: String,
    val front: String,
    val back: String,
    val example: String?,
    val category: Category,
    val difficulty: Difficulty,
    val relatedLessonId: String? = null,
)

/**
 * Per-learner spaced-repetition state for one [Flashcard].
 *
 * Fields mirror the SM-2 algorithm implemented in
 * [com.aimasteracademy.app.domain.srs.SpacedRepetitionScheduler].
 */
data class FlashcardReview(
    val flashcardId: String,
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueEpochDay: Long = 0L,
    val lapses: Int = 0,
    val lastReviewedEpochDay: Long? = null,
) {
    /** A card is "mastered" once it has survived several successful reviews. */
    val isMastered: Boolean get() = repetitions >= MASTERY_REPETITIONS && intervalDays >= 21

    companion object {
        const val MASTERY_REPETITIONS = 4
    }
}

/** How well the learner recalled a card. Maps to an SM-2 quality score. */
enum class RecallGrade(val quality: Int) {
    /** "Review Again" — the card resets. */
    AGAIN(2),

    /** "Know It" — a normal successful recall. */
    KNOWN(4),

    /** "Easy" — recalled instantly; interval grows faster. */
    EASY(5),
}

/** An entry in the searchable AI dictionary. */
data class GlossaryTerm(
    val id: String,
    val term: String,
    val shortDefinition: String,
    val fullDefinition: String,
    val example: String?,
    val category: Category,
    val relatedTermIds: List<String> = emptyList(),
    val relatedLessonId: String? = null,
) {
    /** The A–Z bucket this term sorts into. */
    val initial: Char get() = term.first().uppercaseChar()
}

/** A catalogue entry in the AI Tools library. */
data class AiTool(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val category: AiToolCategory,
    val difficulty: Difficulty,
    val pricing: PricingModel,
    val websiteUrl: String,
    val logoRef: String?,
    val useCases: List<String>,
    val tips: List<String>,
    val tutorialSteps: List<String>,
    val order: Int = 0,
)

enum class AiToolCategory(val id: String) {
    CHAT_ASSISTANT("chat_assistant"),
    CODING("coding"),
    RESEARCH("research"),
    IMAGE_GENERATION("image_generation"),
    VIDEO_GENERATION("video_generation"),
    AUDIO("audio"),
    PRESENTATION("presentation"),
    WRITING("writing"),
    PRODUCTIVITY("productivity"),
    DESIGN("design"),
    EDUCATION("education"),
    DATA_ANALYSIS("data_analysis");

    companion object {
        private val byId = entries.associateBy(AiToolCategory::id)
        fun fromId(id: String): AiToolCategory = byId[id] ?: CHAT_ASSISTANT
    }
}

enum class PricingModel(val id: String) {
    FREE("free"),
    FREEMIUM("freemium"),
    PAID("paid");

    companion object {
        fun fromId(id: String): PricingModel =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FREEMIUM
    }
}

/** A guided, hands-on mini project. */
data class MiniProject(
    val id: String,
    val title: String,
    val goal: String,
    val category: Category,
    val difficulty: Difficulty,
    val estimatedMinutes: Int,
    val prerequisites: List<String>,
    val steps: List<ProjectStep>,
    val expectedOutput: String,
    val challengeExtension: String,
    val xpReward: Int,
)

data class ProjectStep(
    val title: String,
    val explanation: String,
    val code: String?,
    val language: String = "python",
)

/** Anything the learner has saved for later. */
data class Bookmark(
    val id: String,
    val targetId: String,
    val type: BookmarkType,
    val title: String,
    val subtitle: String,
    val createdAtEpochMillis: Long,
)

enum class BookmarkType(val id: String) {
    LESSON("lesson"),
    QUESTION("question"),
    FLASHCARD("flashcard"),
    GLOSSARY_TERM("glossary_term"),
    AI_TOOL("ai_tool"),
    PROJECT("project");

    companion object {
        fun fromId(id: String): BookmarkType =
            entries.firstOrNull { it.id == id } ?: LESSON
    }
}

/** An earned certificate, verifiable by [certificateId]. */
data class Certificate(
    val certificateId: String,
    val trackId: String,
    val courseTitle: String,
    val learnerName: String,
    val issuedAtEpochMillis: Long,
    val verificationUrl: String,
    val scorePercent: Int?,
)
