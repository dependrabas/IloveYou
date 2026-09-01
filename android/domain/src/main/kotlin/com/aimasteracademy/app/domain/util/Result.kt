package com.aimasteracademy.app.domain.util

/**
 * The result of an operation that can fail in a way the UI must handle.
 *
 * Deliberately distinct from Kotlin's built-in `Result`: failures here carry a
 * typed [DomainError] rather than a `Throwable`, which keeps raw exception
 * messages and stack traces out of the presentation layer entirely.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    fun onSuccess(action: (T) -> Unit): Outcome<T> = also {
        if (this is Success) action(data)
    }

    fun onFailure(action: (DomainError) -> Unit): Outcome<T> = also {
        if (this is Failure) action(error)
    }

    companion object {
        fun <T> success(data: T): Outcome<T> = Success(data)
        fun failure(error: DomainError): Outcome<Nothing> = Failure(error)
    }
}

/**
 * Every failure the app can show a learner.
 *
 * Backend messages are never surfaced verbatim; the presentation layer maps each
 * case to a localised string, which is what keeps stack traces and server
 * internals off the screen.
 */
sealed interface DomainError {
    /** No usable connection. */
    data object NoConnection : DomainError

    /** The request reached the server but it failed. [code] is for logging only. */
    data class Server(val code: Int) : DomainError

    /** The session is no longer valid; the user must sign in again. */
    data object SessionExpired : DomainError

    /** Credentials were rejected. */
    data object InvalidCredentials : DomainError

    /** An account already exists for the email. */
    data object EmailAlreadyInUse : DomainError

    /** The password does not meet the policy. */
    data class WeakPassword(val requirement: PasswordRequirement) : DomainError

    /** The requested content is not available (unpublished, or removed). */
    data object ContentUnavailable : DomainError

    /** Local storage failed — disk full, corrupt database. */
    data object Storage : DomainError

    /** The feature requires a premium plan. */
    data object PremiumRequired : DomainError

    /** Too many requests to the tutor proxy. */
    data class RateLimited(val retryAfterSeconds: Int) : DomainError

    /** Anything not otherwise classified. */
    data object Unknown : DomainError
}

/** The specific password rule that was not met. */
enum class PasswordRequirement {
    TOO_SHORT,
    NEEDS_LETTER,
    NEEDS_DIGIT,
    TOO_COMMON,
}
