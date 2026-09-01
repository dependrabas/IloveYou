package com.aimasteracademy.app.domain.repository

import com.aimasteracademy.app.domain.model.Achievement
import com.aimasteracademy.app.domain.model.AiFact
import com.aimasteracademy.app.domain.model.AiTool
import com.aimasteracademy.app.domain.model.Bookmark
import com.aimasteracademy.app.domain.model.BookmarkType
import com.aimasteracademy.app.domain.model.Category
import com.aimasteracademy.app.domain.model.Certificate
import com.aimasteracademy.app.domain.model.Course
import com.aimasteracademy.app.domain.model.CourseProgress
import com.aimasteracademy.app.domain.model.DailyChallenge
import com.aimasteracademy.app.domain.model.Difficulty
import com.aimasteracademy.app.domain.model.ExamBlueprint
import com.aimasteracademy.app.domain.model.Flashcard
import com.aimasteracademy.app.domain.model.FlashcardReview
import com.aimasteracademy.app.domain.model.GlossaryTerm
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
import com.aimasteracademy.app.domain.model.User
import com.aimasteracademy.app.domain.model.UserStats
import com.aimasteracademy.app.domain.model.XpAward
import com.aimasteracademy.app.domain.util.DomainError
import com.aimasteracademy.app.domain.util.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts.
 *
 * These interfaces are the boundary between the business rules and everything
 * that can fail or change: Room, DataStore, Retrofit, Firebase. Nothing in this
 * module knows which of those is in play, which is what makes the rules testable
 * with plain fakes.
 *
 * Read APIs return [Flow] so the UI is always driven by the database rather than
 * by one-shot fetches; write APIs are `suspend` and return [Outcome] when they
 * can fail in a way a learner must see.
 */

interface AuthRepository {
    /** The current user, or `null` when nobody (not even a guest) is signed in. */
    val currentUser: Flow<User?>

    suspend fun signUp(name: String, email: String, password: String): Outcome<User>
    suspend fun signIn(email: String, password: String): Outcome<User>
    suspend fun signInWithGoogle(idToken: String): Outcome<User>
    suspend fun continueAsGuest(): Outcome<User>
    suspend fun sendPasswordReset(email: String): Outcome<Unit>
    suspend fun sendEmailVerification(): Outcome<Unit>
    suspend fun refreshVerificationStatus(): Outcome<Boolean>
    suspend fun signOut(): Outcome<Unit>

    /** Deletes the account and every trace of its local data. */
    suspend fun deleteAccount(): Outcome<Unit>

    /** Promotes a guest to a real account, keeping all local progress. */
    suspend fun linkGuestToAccount(name: String, email: String, password: String): Outcome<User>
}

interface UserRepository {
    val profile: Flow<LearnerProfile>
    val stats: Flow<UserStats>

    suspend fun saveProfile(profile: LearnerProfile): Outcome<Unit>
    suspend fun updateDisplayName(name: String): Outcome<Unit>
    suspend fun updateAvatar(avatarRef: String): Outcome<Unit>
    suspend fun recordStudyTime(minutes: Int)
}

interface ContentRepository {
    fun observeTracks(): Flow<List<LearningTrack>>
    fun observeCourses(trackId: String): Flow<List<Course>>
    fun observeModules(courseId: String): Flow<List<Module>>
    fun observeLessons(moduleId: String): Flow<List<Lesson>>
    fun observeAllLessons(): Flow<List<Lesson>>

    suspend fun getTrack(id: String): LearningTrack?
    suspend fun getCourse(id: String): Course?
    suspend fun getLesson(id: String): Lesson?
    suspend fun getLessonsForCourse(courseId: String): List<Lesson>
    suspend fun getProjects(): List<MiniProject>
    suspend fun getProject(id: String): MiniProject?

    /** Pulls any content published since the last sync. Safe to call offline. */
    suspend fun refreshRemoteContent(): Outcome<Unit>
}

interface ProgressRepository {
    fun observeLessonProgress(): Flow<List<LessonProgress>>
    fun observeCourseProgress(courseId: String): Flow<CourseProgress>
    fun observeTopicPerformance(): Flow<List<TopicPerformance>>

    suspend fun getLessonStatus(lessonId: String): ProgressStatus
    suspend fun markLessonOpened(lesson: Lesson)
    suspend fun saveScrollPosition(lessonId: String, fraction: Float)
    suspend fun completeLesson(lesson: Lesson): Outcome<Int>

    /** The lesson to resume on the dashboard's Continue Learning card. */
    fun observeInProgressLesson(): Flow<Lesson?>

    suspend fun completedCourseIds(): Set<String>
    suspend fun completedTrackIds(): Set<String>
}

interface QuestionRepository {
    suspend fun getQuestions(ids: List<String>): List<Question>
    suspend fun getQuestion(id: String): Question?

    suspend fun pool(
        categories: Set<Category> = emptySet(),
        difficulties: Set<Difficulty> = emptySet(),
    ): List<Question>

    /** Ids answered recently, used to avoid repeats. */
    suspend fun recentlySeenIds(withinDays: Int = 14): Set<String>

    /** Questions the learner has previously got wrong, for Mistake Review. */
    suspend fun incorrectlyAnsweredIds(): List<String>

    suspend fun totalQuestionCount(): Int
}

interface QuizRepository {
    fun observeAttempts(limit: Int = 20): Flow<List<QuizAttempt>>

    suspend fun saveAttempt(attempt: QuizAttempt): Outcome<Unit>
    suspend fun getAttempt(id: String): QuizAttempt?
    suspend fun examBlueprints(): List<ExamBlueprint>
    suspend fun examBlueprint(id: String): ExamBlueprint?

    suspend fun dailyChallenge(epochDay: Long): Outcome<DailyChallenge>
    suspend fun isDailyChallengeCompleted(epochDay: Long): Boolean
    suspend fun dailyQuestion(epochDay: Long): Outcome<Question>
    suspend fun isDailyQuestionAnswered(epochDay: Long): Boolean
    suspend fun dailyChallengesCompletedCount(): Int
    suspend fun mockExamsPassedCount(): Int
}

interface GamificationRepository {
    val totalXp: Flow<Int>
    val xpLedger: Flow<List<XpAward>>
    val unlockedAchievements: Flow<List<UnlockedAchievement>>

    /** Achievements unlocked but not yet celebrated on screen. */
    val pendingCelebrations: Flow<List<Achievement>>

    suspend fun achievementCatalogue(): List<Achievement>
    suspend fun awardXp(award: XpAward)
    suspend fun unlockAchievements(achievements: List<Achievement>)
    suspend fun markCelebrationSeen(achievementId: String)

    fun observeStreak(): Flow<com.aimasteracademy.app.domain.gamification.StreakState>
    suspend fun recordStudyDay(epochDay: Long): Outcome<Int>

    fun observeLeaderboard(scope: LeaderboardScope): Flow<List<LeaderboardEntry>>
    suspend fun refreshLeaderboard(scope: LeaderboardScope): Outcome<Unit>
}

interface FlashcardRepository {
    fun observeDeck(category: Category?): Flow<List<Flashcard>>
    fun observeDueCount(todayEpochDay: Long): Flow<Int>

    suspend fun dueCards(todayEpochDay: Long, category: Category?, limit: Int): List<Flashcard>
    suspend fun reviewState(flashcardId: String): FlashcardReview?
    suspend fun saveReview(review: FlashcardReview)
    suspend fun masteredCount(): Int
}

interface GlossaryRepository {
    fun observeTerms(query: String = ""): Flow<List<GlossaryTerm>>
    suspend fun getTerm(id: String): GlossaryTerm?
    suspend fun termsByInitial(): Map<Char, List<GlossaryTerm>>
}

interface AiToolRepository {
    fun observeTools(category: com.aimasteracademy.app.domain.model.AiToolCategory?): Flow<List<AiTool>>
    fun observeToolOfTheDay(epochDay: Long): Flow<AiTool?>
    suspend fun getTool(id: String): AiTool?
    suspend fun refresh(): Outcome<Unit>
}

interface BookmarkRepository {
    fun observeBookmarks(type: BookmarkType? = null): Flow<List<Bookmark>>
    fun observeIsBookmarked(targetId: String): Flow<Boolean>
    suspend fun toggle(bookmark: Bookmark): Boolean
    suspend fun remove(targetId: String)
}

interface CertificateRepository {
    fun observeCertificates(): Flow<List<Certificate>>
    suspend fun issueCertificate(trackId: String, scorePercent: Int?): Outcome<Certificate>
    suspend fun getCertificate(id: String): Certificate?
}

interface FactRepository {
    suspend fun factOfTheDay(epochDay: Long): AiFact?
}

/**
 * The AI Tutor.
 *
 * The contract deliberately exposes no API key or model name: the app talks to
 * a first-party proxy that holds the provider credentials server-side. See
 * `data/network/TutorApi` for the reasoning.
 */
interface TutorRepository {
    fun observeConversation(conversationId: String): Flow<List<TutorMessage>>

    suspend fun startConversation(): String

    /** Streams the assistant's reply token-by-token. */
    fun sendMessage(
        conversationId: String,
        message: String,
        level: com.aimasteracademy.app.domain.model.KnowledgeLevel,
    ): Flow<TutorStreamEvent>

    suspend fun clearConversation(conversationId: String)
    suspend fun suggestedPrompts(): List<String>
}

data class TutorMessage(
    val id: String,
    val conversationId: String,
    val role: TutorRole,
    val content: String,
    val createdAtEpochMillis: Long,
    val isStreaming: Boolean = false,
)

enum class TutorRole { USER, ASSISTANT }

sealed interface TutorStreamEvent {
    data class Token(val text: String) : TutorStreamEvent
    data class Completed(val message: TutorMessage) : TutorStreamEvent
    data class Failed(val error: DomainError) : TutorStreamEvent
}

interface SearchRepository {
    suspend fun search(query: String): SearchResults
    fun observeHistory(): Flow<List<String>>
    suspend fun recordQuery(query: String)
    suspend fun clearHistory()
}

data class SearchResults(
    val lessons: List<Lesson> = emptyList(),
    val questions: List<Question> = emptyList(),
    val glossaryTerms: List<GlossaryTerm> = emptyList(),
    val tools: List<AiTool> = emptyList(),
    val courses: List<Course> = emptyList(),
) {
    val isEmpty: Boolean
        get() = lessons.isEmpty() && questions.isEmpty() && glossaryTerms.isEmpty() &&
            tools.isEmpty() && courses.isEmpty()

    val totalCount: Int
        get() = lessons.size + questions.size + glossaryTerms.size + tools.size + courses.size
}

interface SyncRepository {
    val syncState: Flow<SyncState>
    suspend fun syncNow(): Outcome<Unit>
    suspend fun scheduleBackgroundSync()
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Synced(val atEpochMillis: Long) : SyncState
    data class Failed(val error: DomainError) : SyncState
}
