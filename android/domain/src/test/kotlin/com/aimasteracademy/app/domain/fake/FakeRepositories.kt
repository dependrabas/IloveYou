package com.aimasteracademy.app.domain.fake

import com.aimasteracademy.app.domain.gamification.StreakCalculator
import com.aimasteracademy.app.domain.gamification.StreakState
import com.aimasteracademy.app.domain.model.Achievement
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Course
import com.aimasteracademy.app.domain.model.CourseProgress
import com.aimasteracademy.app.domain.model.DailyChallenge
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.ExamBlueprint
import com.aimasteracademy.app.domain.model.Flashcard
import com.aimasteracademy.app.domain.model.FlashcardReview
import com.aimasteracademy.app.domain.model.LearnerProfile
import com.aimasteracademy.app.domain.model.LearningTrack
import com.aimasteracademy.app.domain.model.LeaderboardEntry
import com.aimasteracademy.app.domain.model.LeaderboardScope
import com.aimasteracademy.app.domain.model.Lesson
import com.aimasteracademy.app.domain.model.LessonProgress
import com.aimasteracademy.app.domain.model.MiniProject
import com.aimasteracademy.app.domain.model.Module
import com.aimasteracademy.app.domain.model.ProgressStatus
import com.aimasteracademy.app.domain.model.Question
import com.aimasteracademy.app.domain.model.QuizAttempt
import com.aimasteracademy.app.domain.model.TopicPerformance
import com.aimasteracademy.app.domain.model.UnlockedAchievement
import com.aimasteracademy.app.domain.model.UserStats
import com.aimasteracademy.app.domain.model.XpAward
import com.aimasteracademy.app.domain.repository.ContentRepository
import com.aimasteracademy.app.domain.repository.GamificationRepository
import com.aimasteracademy.app.domain.repository.ProgressRepository
import com.aimasteracademy.app.domain.repository.QuestionRepository
import com.aimasteracademy.app.domain.repository.QuizRepository
import com.aimasteracademy.app.domain.repository.UserRepository
import com.aimasteracademy.app.domain.util.DomainError
import com.aimasteracademy.app.domain.util.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory repository doubles.
 *
 * Hand-written rather than mocked: the use cases under test coordinate several
 * repositories at once, and a fake that actually stores state catches ordering
 * bugs (double-awarding XP, for instance) that stubbed calls would not.
 */

class FakeQuestionRepository(
    private val questions: MutableList<Question> = mutableListOf(),
) : QuestionRepository {

    var recentlySeen: Set<String> = emptySet()
    var incorrectIds: List<String> = emptyList()

    fun seed(vararg newQuestions: Question) {
        questions += newQuestions
    }

    override suspend fun getQuestions(ids: List<String>): List<Question> {
        val byId = questions.associateBy(Question::id)
        return ids.mapNotNull(byId::get)
    }

    override suspend fun getQuestion(id: String): Question? = questions.firstOrNull { it.id == id }

    override suspend fun pool(
        categories: Set<Category>,
        difficulties: Set<Difficulty>,
    ): List<Question> = questions.filter {
        (categories.isEmpty() || it.category in categories) &&
            (difficulties.isEmpty() || it.difficulty in difficulties)
    }

    override suspend fun recentlySeenIds(withinDays: Int): Set<String> = recentlySeen

    override suspend fun incorrectlyAnsweredIds(): List<String> = incorrectIds

    override suspend fun totalQuestionCount(): Int = questions.size
}

class FakeQuizRepository : QuizRepository {
    val savedAttempts = mutableListOf<QuizAttempt>()
    var dailyChallengeQuestionIds: List<String> = emptyList()
    var dailyChallengesCompleted = 0
    var examsPassed = 0
    var saveShouldFail = false

    private val attempts = MutableStateFlow<List<QuizAttempt>>(emptyList())

    override fun observeAttempts(limit: Int): Flow<List<QuizAttempt>> =
        attempts.map { it.take(limit) }

    override suspend fun saveAttempt(attempt: QuizAttempt): Outcome<Unit> {
        if (saveShouldFail) return Outcome.failure(DomainError.Storage)
        savedAttempts += attempt
        attempts.value = savedAttempts.toList()
        return Outcome.success(Unit)
    }

    override suspend fun getAttempt(id: String): QuizAttempt? =
        savedAttempts.firstOrNull { it.id == id }

    override suspend fun examBlueprints(): List<ExamBlueprint> = emptyList()

    override suspend fun examBlueprint(id: String): ExamBlueprint? = null

    override suspend fun dailyChallenge(epochDay: Long): Outcome<DailyChallenge> =
        Outcome.success(
            DailyChallenge(
                id = "daily-$epochDay",
                epochDay = epochDay,
                title = "Daily AI Challenge",
                questionIds = dailyChallengeQuestionIds,
                xpReward = 50,
                bonusXpForPerfect = 50,
            ),
        )

    override suspend fun isDailyChallengeCompleted(epochDay: Long): Boolean = false

    override suspend fun dailyQuestion(epochDay: Long): Outcome<Question> =
        Outcome.failure(DomainError.ContentUnavailable)

    override suspend fun isDailyQuestionAnswered(epochDay: Long): Boolean = false

    override suspend fun dailyChallengesCompletedCount(): Int = dailyChallengesCompleted

    override suspend fun mockExamsPassedCount(): Int = examsPassed
}

class FakeGamificationRepository(
    private val catalogue: List<Achievement> = emptyList(),
) : GamificationRepository {

    private val xp = MutableStateFlow(0)
    private val ledger = MutableStateFlow<List<XpAward>>(emptyList())
    private val unlocked = MutableStateFlow<List<UnlockedAchievement>>(emptyList())
    private val streak = MutableStateFlow(StreakState())
    private val calculator = StreakCalculator()

    /** Every award that reached the repository, for asserting on ordering. */
    val awards = mutableListOf<XpAward>()

    override val totalXp: Flow<Int> = xp.asStateFlow()
    override val xpLedger: Flow<List<XpAward>> = ledger.asStateFlow()
    override val unlockedAchievements: Flow<List<UnlockedAchievement>> = unlocked.asStateFlow()
    override val pendingCelebrations: Flow<List<Achievement>> =
        unlocked.map { list ->
            val pendingIds = list.filterNot(UnlockedAchievement::seenByUser).map { it.achievementId }
            catalogue.filter { it.id in pendingIds }
        }

    override suspend fun achievementCatalogue(): List<Achievement> = catalogue

    override suspend fun awardXp(award: XpAward) {
        awards += award
        xp.value += award.amount
        ledger.value = ledger.value + award
    }

    override suspend fun unlockAchievements(achievements: List<Achievement>) {
        unlocked.value = unlocked.value + achievements.map {
            UnlockedAchievement(it.id, 0L)
        }
        // Achievements pay out their own XP reward.
        achievements.forEach { xp.value += it.xpReward }
    }

    override suspend fun markCelebrationSeen(achievementId: String) {
        unlocked.value = unlocked.value.map {
            if (it.achievementId == achievementId) it.copy(seenByUser = true) else it
        }
    }

    override fun observeStreak(): Flow<StreakState> = streak.asStateFlow()

    override suspend fun recordStudyDay(epochDay: Long): Outcome<Int> {
        val update = calculator.recordActivity(streak.value, epochDay)
        streak.value = update.state
        return Outcome.success(update.state.currentStreak)
    }

    override fun observeLeaderboard(scope: LeaderboardScope): Flow<List<LeaderboardEntry>> =
        MutableStateFlow(emptyList())

    override suspend fun refreshLeaderboard(scope: LeaderboardScope): Outcome<Unit> =
        Outcome.success(Unit)
}

class FakeUserRepository(initialStats: UserStats = UserStats()) : UserRepository {
    private val profileFlow = MutableStateFlow(LearnerProfile())
    private val statsFlow = MutableStateFlow(initialStats)

    var recordedStudyMinutes = 0

    override val profile: Flow<LearnerProfile> = profileFlow.asStateFlow()
    override val stats: Flow<UserStats> = statsFlow.asStateFlow()

    fun setStats(stats: UserStats) {
        statsFlow.value = stats
    }

    override suspend fun saveProfile(profile: LearnerProfile): Outcome<Unit> {
        profileFlow.value = profile
        return Outcome.success(Unit)
    }

    override suspend fun updateDisplayName(name: String): Outcome<Unit> = Outcome.success(Unit)

    override suspend fun updateAvatar(avatarRef: String): Outcome<Unit> = Outcome.success(Unit)

    override suspend fun recordStudyTime(minutes: Int) {
        recordedStudyMinutes += minutes
    }
}

class FakeProgressRepository : ProgressRepository {
    private val statuses = mutableMapOf<String, ProgressStatus>()
    private val progressFlow = MutableStateFlow<List<LessonProgress>>(emptyList())

    var completedCourses = mutableSetOf<String>()
    var completedTracks = mutableSetOf<String>()
    var completeShouldFail = false

    fun setStatus(lessonId: String, status: ProgressStatus) {
        statuses[lessonId] = status
    }

    override fun observeLessonProgress(): Flow<List<LessonProgress>> = progressFlow.asStateFlow()

    override fun observeCourseProgress(courseId: String): Flow<CourseProgress> =
        MutableStateFlow(CourseProgress(courseId, 0, 0, null))

    override fun observeTopicPerformance(): Flow<List<TopicPerformance>> =
        MutableStateFlow(emptyList())

    override suspend fun getLessonStatus(lessonId: String): ProgressStatus =
        statuses[lessonId] ?: ProgressStatus.NOT_STARTED

    override suspend fun markLessonOpened(lesson: Lesson) {
        statuses[lesson.id] = ProgressStatus.IN_PROGRESS
    }

    override suspend fun saveScrollPosition(lessonId: String, fraction: Float) = Unit

    override suspend fun completeLesson(lesson: Lesson): Outcome<Int> {
        if (completeShouldFail) return Outcome.failure(DomainError.Storage)
        statuses[lesson.id] = ProgressStatus.COMPLETED
        return Outcome.success(1)
    }

    override fun observeInProgressLesson(): Flow<Lesson?> = MutableStateFlow(null)

    override suspend fun completedCourseIds(): Set<String> = completedCourses

    override suspend fun completedTrackIds(): Set<String> = completedTracks
}

class FakeContentRepository(
    private val lessons: MutableList<Lesson> = mutableListOf(),
    private val tracks: MutableList<LearningTrack> = mutableListOf(),
) : ContentRepository {

    fun seedLessons(vararg newLessons: Lesson) {
        lessons += newLessons
    }

    fun seedTracks(vararg newTracks: LearningTrack) {
        tracks += newTracks
    }

    override fun observeTracks(): Flow<List<LearningTrack>> = MutableStateFlow(tracks.toList())

    override fun observeCourses(trackId: String): Flow<List<Course>> = MutableStateFlow(emptyList())

    override fun observeModules(courseId: String): Flow<List<Module>> = MutableStateFlow(emptyList())

    override fun observeLessons(moduleId: String): Flow<List<Lesson>> =
        MutableStateFlow(lessons.filter { it.moduleId == moduleId })

    override fun observeAllLessons(): Flow<List<Lesson>> = MutableStateFlow(lessons.toList())

    override suspend fun getTrack(id: String): LearningTrack? = tracks.firstOrNull { it.id == id }

    override suspend fun getCourse(id: String): Course? = null

    override suspend fun getLesson(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    override suspend fun getLessonsForCourse(courseId: String): List<Lesson> =
        lessons.filter { it.courseId == courseId }

    override suspend fun getProjects(): List<MiniProject> = emptyList()

    override suspend fun getProject(id: String): MiniProject? = null

    override suspend fun refreshRemoteContent(): Outcome<Unit> = Outcome.success(Unit)
}

class FakeFlashcardRepository : com.aimasteracademy.app.domain.repository.FlashcardRepository {
    private val reviews = mutableMapOf<String, FlashcardReview>()
    val savedReviews = mutableListOf<FlashcardReview>()
    var deck: List<Flashcard> = emptyList()

    fun setReview(review: FlashcardReview) {
        reviews[review.flashcardId] = review
    }

    override fun observeDeck(category: Category?): Flow<List<Flashcard>> = MutableStateFlow(deck)

    override fun observeDueCount(todayEpochDay: Long): Flow<Int> = MutableStateFlow(deck.size)

    override suspend fun dueCards(
        todayEpochDay: Long,
        category: Category?,
        limit: Int,
    ): List<Flashcard> = deck
        .filter { category == null || it.category == category }
        .filter { (reviews[it.id]?.dueEpochDay ?: 0L) <= todayEpochDay }
        .take(limit)

    override suspend fun reviewState(flashcardId: String): FlashcardReview? = reviews[flashcardId]

    override suspend fun saveReview(review: FlashcardReview) {
        reviews[review.flashcardId] = review
        savedReviews += review
    }

    override suspend fun masteredCount(): Int = reviews.values.count(FlashcardReview::isMastered)
}
