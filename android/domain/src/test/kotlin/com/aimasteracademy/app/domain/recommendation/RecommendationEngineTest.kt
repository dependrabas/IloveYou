package com.aimasteracademy.app.domain.recommendation

import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.KnowledgeLevel
import com.aimasteracademy.app.domain.model.LearnerProfile
import com.aimasteracademy.app.domain.model.Lesson
import com.aimasteracademy.app.domain.model.ProgressStatus
import com.aimasteracademy.app.domain.model.TopicPerformance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecommendationEngineTest {

    private val engine = RecommendationEngine()

    private fun lesson(
        id: String,
        category: Category,
        difficulty: Difficulty = Difficulty.EASY,
        order: Int = 0,
        minutes: Int = 8,
    ) = Lesson(
        id = id,
        moduleId = "m1",
        courseId = "c1",
        trackId = "t1",
        title = "Lesson $id",
        subtitle = "",
        category = category,
        difficulty = difficulty,
        estimatedMinutes = minutes,
        blocks = emptyList(),
        summaryPoints = emptyList(),
        quizQuestionIds = emptyList(),
        order = order,
    )

    private fun performance(category: Category, answered: Int, correct: Int) =
        TopicPerformance(category, answered, correct, 0, null)

    @Test
    fun `a weak topic outranks everything else`() {
        val lessons = listOf(
            lesson("intro", Category.AI_FUNDAMENTALS, order = 0),
            lesson("nn", Category.DEEP_LEARNING, order = 5),
        )

        val result = engine.recommend(
            RecommendationInput(
                profile = LearnerProfile(knowledgeLevel = KnowledgeLevel.BEGINNER),
                allLessons = lessons,
                lessonStatus = emptyMap(),
                // 2 of 10 correct in deep learning.
                topicPerformance = mapOf(
                    Category.DEEP_LEARNING to performance(Category.DEEP_LEARNING, 10, 2),
                ),
            ),
        )

        assertThat(result.first().lesson.id).isEqualTo("nn")
        assertThat(result.first().reason).isEqualTo(RecommendationReason.WEAK_TOPIC)
        // The rationale must be specific enough to motivate action.
        assertThat(result.first().rationale).contains("8")
    }

    @Test
    fun `a topic with too few answers is not called weak`() {
        val performance = mapOf(
            // 1 of 3 correct — bad, but not yet enough evidence.
            Category.RAG to performance(Category.RAG, 3, 1),
        )

        assertThat(engine.weakCategories(performance)).isEmpty()

        val withEvidence = mapOf(Category.RAG to performance(Category.RAG, 10, 3))
        assertThat(engine.weakCategories(withEvidence)).containsExactly(Category.RAG)
    }

    @Test
    fun `weak categories are ordered worst first`() {
        val performance = mapOf(
            Category.NLP to performance(Category.NLP, 10, 6),
            Category.RAG to performance(Category.RAG, 10, 2),
            Category.LLMS to performance(Category.LLMS, 10, 4),
        )

        assertThat(engine.weakCategories(performance))
            .containsExactly(Category.RAG, Category.LLMS, Category.NLP).inOrder()
    }

    @Test
    fun `completed lessons are never recommended`() {
        val lessons = listOf(
            lesson("a", Category.AI_FUNDAMENTALS, order = 0),
            lesson("b", Category.AI_FUNDAMENTALS, order = 1),
        )

        val result = engine.recommend(
            RecommendationInput(
                profile = LearnerProfile(),
                allLessons = lessons,
                lessonStatus = mapOf("a" to ProgressStatus.COMPLETED),
                topicPerformance = emptyMap(),
            ),
        )

        assertThat(result.map { it.lesson.id }).containsExactly("b")
    }

    @Test
    fun `an in-progress lesson is prioritised over an untouched one`() {
        val lessons = listOf(
            lesson("fresh", Category.AI_FUNDAMENTALS, order = 0),
            lesson("started", Category.AI_FUNDAMENTALS, order = 9),
        )

        val result = engine.recommend(
            RecommendationInput(
                profile = LearnerProfile(),
                allLessons = lessons,
                lessonStatus = mapOf("started" to ProgressStatus.IN_PROGRESS),
                topicPerformance = emptyMap(),
            ),
        )

        assertThat(result.first().lesson.id).isEqualTo("started")
        assertThat(result.first().reason).isEqualTo(RecommendationReason.CONTINUE_COURSE)
    }

    @Test
    fun `declared interests are favoured over unrelated topics`() {
        val lessons = listOf(
            lesson("robots", Category.ROBOTICS, order = 0),
            lesson("prompting", Category.PROMPT_ENGINEERING, order = 1),
        )

        val result = engine.recommend(
            RecommendationInput(
                profile = LearnerProfile(
                    interestedCategories = listOf(Category.PROMPT_ENGINEERING),
                ),
                allLessons = lessons,
                lessonStatus = emptyMap(),
                topicPerformance = emptyMap(),
            ),
        )

        assertThat(result.first().lesson.id).isEqualTo("prompting")
        assertThat(result.first().reason).isEqualTo(RecommendationReason.MATCHES_INTEREST)
    }

    @Test
    fun `beginners are not handed expert lessons first`() {
        val lessons = listOf(
            lesson("expert", Category.AI_FUNDAMENTALS, Difficulty.EXPERT, order = 0),
            lesson("easy", Category.AI_FUNDAMENTALS, Difficulty.EASY, order = 1),
        )

        val result = engine.recommend(
            RecommendationInput(
                profile = LearnerProfile(knowledgeLevel = KnowledgeLevel.BEGINNER),
                allLessons = lessons,
                lessonStatus = emptyMap(),
                topicPerformance = emptyMap(),
            ),
        )

        assertThat(result.first().lesson.id).isEqualTo("easy")
    }

    @Test
    fun `advanced learners are not pushed the easiest material`() {
        val lessons = listOf(
            lesson("easy", Category.DEEP_LEARNING, Difficulty.EASY, order = 0),
            lesson("hard", Category.DEEP_LEARNING, Difficulty.HARD, order = 1),
        )

        val result = engine.recommend(
            RecommendationInput(
                profile = LearnerProfile(knowledgeLevel = KnowledgeLevel.ADVANCED),
                allLessons = lessons,
                lessonStatus = emptyMap(),
                topicPerformance = emptyMap(),
            ),
        )

        assertThat(result.first().lesson.id).isEqualTo("hard")
    }

    @Test
    fun `the recommendation limit is respected`() {
        val lessons = (1..20).map { lesson("l$it", Category.AI_FUNDAMENTALS, order = it) }

        val result = engine.recommend(
            RecommendationInput(LearnerProfile(), lessons, emptyMap(), emptyMap()),
            limit = 3,
        )

        assertThat(result).hasSize(3)
    }

    @Test
    fun `continue learning resumes the in-progress lesson`() {
        val lessons = listOf(lesson("a", Category.NLP), lesson("b", Category.NLP))

        val result = engine.continueLearning(
            RecommendationInput(
                profile = LearnerProfile(),
                allLessons = lessons,
                lessonStatus = emptyMap(),
                topicPerformance = emptyMap(),
                inProgressLessonId = "b",
            ),
        )

        assertThat(result?.lesson?.id).isEqualTo("b")
        assertThat(result?.reason).isEqualTo(RecommendationReason.CONTINUE_COURSE)
    }

    @Test
    fun `a fully complete curriculum still offers refreshers instead of nothing`() {
        val lessons = listOf(lesson("a", Category.NLP), lesson("b", Category.NLP))

        val result = engine.recommend(
            RecommendationInput(
                profile = LearnerProfile(),
                allLessons = lessons,
                lessonStatus = lessons.associate { it.id to ProgressStatus.COMPLETED },
                topicPerformance = emptyMap(),
            ),
        )

        assertThat(result).isNotEmpty()
        assertThat(result.first().reason).isEqualTo(RecommendationReason.REFRESHER)
    }

    @Test
    fun `an empty curriculum yields no recommendations rather than crashing`() {
        val result = engine.recommend(
            RecommendationInput(LearnerProfile(), emptyList(), emptyMap(), emptyMap()),
        )

        assertThat(result).isEmpty()
    }
}
