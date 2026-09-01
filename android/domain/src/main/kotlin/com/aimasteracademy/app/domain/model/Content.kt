package com.aimasteracademy.app.domain.model

/**
 * A top-level learning track, e.g. "AI Fundamentals" or "Prompt Engineering".
 * Completing every course in a track unlocks a [Certificate].
 */
data class LearningTrack(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val category: Category,
    val level: KnowledgeLevel,
    val courseIds: List<String>,
    val accentColor: Long,
    val iconRef: String,
    val order: Int,
    val isPremium: Boolean = false,
    val certificateTitle: String? = null,
)

/** A course groups modules; a module groups lessons. */
data class Course(
    val id: String,
    val trackId: String,
    val title: String,
    val description: String,
    val category: Category,
    val difficulty: Difficulty,
    val estimatedMinutes: Int,
    val moduleIds: List<String>,
    val order: Int,
    val isPremium: Boolean = false,
)

data class Module(
    val id: String,
    val courseId: String,
    val title: String,
    val summary: String,
    val lessonIds: List<String>,
    val order: Int,
)

/**
 * One readable lesson, composed of ordered [blocks].
 *
 * Rendering is driven entirely by the block list, which is what lets new content
 * shapes (a new callout style, a new diagram) ship from the backend without an
 * app release.
 */
data class Lesson(
    val id: String,
    val moduleId: String,
    val courseId: String,
    val trackId: String,
    val title: String,
    val subtitle: String,
    val category: Category,
    val difficulty: Difficulty,
    val estimatedMinutes: Int,
    val blocks: List<LessonBlock>,
    val summaryPoints: List<String>,
    val quizQuestionIds: List<String>,
    val order: Int,
    val isPremium: Boolean = false,
) {
    /** Rough reading time used when content does not declare one. */
    val wordCount: Int
        get() = blocks.sumOf { block ->
            when (block) {
                is LessonBlock.Paragraph -> block.text.split(' ').size
                is LessonBlock.Heading -> block.text.split(' ').size
                is LessonBlock.Callout -> block.body.split(' ').size
                is LessonBlock.BulletList -> block.items.sumOf { it.split(' ').size }
                else -> 0
            }
        }
}

/**
 * The renderable pieces of a lesson.
 *
 * Modelled as a sealed hierarchy so the Compose renderer is an exhaustive `when`
 * — adding a block type is a compile error until every renderer handles it.
 */
sealed interface LessonBlock {
    data class Heading(val text: String, val level: Int = 2) : LessonBlock

    data class Paragraph(val text: String) : LessonBlock

    data class BulletList(val items: List<String>, val ordered: Boolean = false) : LessonBlock

    /** A highlighted aside. [style] maps to a colour/icon pair in the design system. */
    data class Callout(
        val title: String,
        val body: String,
        val style: CalloutStyle = CalloutStyle.NOTE,
    ) : LessonBlock

    data class Code(
        val code: String,
        val language: String = "python",
        val caption: String? = null,
    ) : LessonBlock

    /** A labelled flow diagram drawn natively, e.g. the stages of a RAG pipeline. */
    data class Diagram(
        val title: String,
        val nodes: List<String>,
        val caption: String? = null,
    ) : LessonBlock

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val caption: String? = null,
    ) : LessonBlock

    /** An inline comprehension check embedded mid-lesson. */
    data class InlineQuestion(val questionId: String) : LessonBlock

    data class Divider(val label: String? = null) : LessonBlock
}

enum class CalloutStyle {
    NOTE,
    TIP,
    WARNING,
    DID_YOU_KNOW,
    KEY_IDEA,
    ;

    companion object {
        fun fromId(id: String): CalloutStyle =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: NOTE
    }
}
