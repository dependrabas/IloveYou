package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.model.User
import com.aimasteracademy.app.domain.repository.AuthRepository
import com.aimasteracademy.app.domain.util.DomainError
import com.aimasteracademy.app.domain.util.Outcome
import com.aimasteracademy.app.domain.util.Validators

/**
 * Validates sign-up input before any network call.
 *
 * Returning a typed [DomainError] (rather than a message) keeps all user-facing
 * copy in the resource files, which is what makes the app translatable.
 */
class SignUpUseCase(private val authRepository: AuthRepository) {

    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): Outcome<User> {
        if (!Validators.isValidName(name)) {
            return Outcome.failure(DomainError.Unknown)
        }
        if (!Validators.isValidEmail(email)) {
            return Outcome.failure(DomainError.InvalidCredentials)
        }
        Validators.validatePassword(password)?.let { requirement ->
            return Outcome.failure(DomainError.WeakPassword(requirement))
        }
        if (password != confirmPassword) {
            return Outcome.failure(DomainError.InvalidCredentials)
        }
        return authRepository.signUp(name.trim(), email.trim().lowercase(), password)
    }
}

class SignInUseCase(private val authRepository: AuthRepository) {

    suspend operator fun invoke(email: String, password: String): Outcome<User> {
        if (!Validators.isValidEmail(email) || password.isEmpty()) {
            return Outcome.failure(DomainError.InvalidCredentials)
        }
        return authRepository.signIn(email.trim().lowercase(), password)
    }
}

/**
 * Signs out, or — for a guest — warns first.
 *
 * A guest's progress is local only, so signing out would discard it. The UI uses
 * [requiresDataLossWarning] to show a confirmation instead of silently wiping
 * weeks of study.
 */
class SignOutUseCase(private val authRepository: AuthRepository) {

    suspend fun requiresDataLossWarning(user: User?): Boolean = user?.isGuest == true

    suspend operator fun invoke(): Outcome<Unit> = authRepository.signOut()
}
