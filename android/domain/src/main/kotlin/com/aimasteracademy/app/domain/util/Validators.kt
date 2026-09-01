package com.aimasteracademy.app.domain.util

/**
 * Input validation shared by the sign-up, login and profile screens.
 *
 * Validation lives in the domain layer so the same rules apply to every entry
 * point, and so they can be unit-tested without a UI.
 */
object Validators {

    private val EMAIL_PATTERN = Regex(
        "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$",
    )

    /**
     * A deliberately small list of passwords that are common enough to be
     * guessed immediately. It is not a substitute for server-side checks — it
     * exists to give instant feedback before a network round trip.
     */
    private val COMMON_PASSWORDS = setOf(
        "password", "password1", "12345678", "123456789", "qwerty123",
        "letmein1", "welcome1", "abc12345", "iloveyou", "admin123",
    )

    const val MIN_PASSWORD_LENGTH = 8
    const val MIN_NAME_LENGTH = 2
    const val MAX_NAME_LENGTH = 50

    fun isValidEmail(email: String): Boolean =
        email.trim().let { it.length in 3..254 && EMAIL_PATTERN.matches(it) }

    fun isValidName(name: String): Boolean =
        name.trim().length in MIN_NAME_LENGTH..MAX_NAME_LENGTH

    /**
     * Returns the first unmet password requirement, or `null` when the password
     * is acceptable.
     */
    fun validatePassword(password: String): PasswordRequirement? = when {
        password.length < MIN_PASSWORD_LENGTH -> PasswordRequirement.TOO_SHORT
        password.none(Char::isLetter) -> PasswordRequirement.NEEDS_LETTER
        password.none(Char::isDigit) -> PasswordRequirement.NEEDS_DIGIT
        password.lowercase() in COMMON_PASSWORDS -> PasswordRequirement.TOO_COMMON
        else -> null
    }

    /** A coarse 0..4 strength score for the sign-up meter. */
    fun passwordStrength(password: String): Int {
        if (password.isEmpty()) return 0
        var score = 0
        if (password.length >= MIN_PASSWORD_LENGTH) score++
        if (password.length >= 12) score++
        if (password.any(Char::isUpperCase) && password.any(Char::isLowerCase)) score++
        if (password.any(Char::isDigit) && password.any { !it.isLetterOrDigit() }) score++
        if (password.lowercase() in COMMON_PASSWORDS) return 0
        return score.coerceIn(0, 4)
    }

    /**
     * Derives a display username from a name, e.g. "Dependra Bas" -> "dependrabas".
     * Falls back to a stable prefix when the name has no usable characters.
     */
    fun suggestUsername(name: String, suffix: String = ""): String {
        val base = name.lowercase()
            .filter { it.isLetterOrDigit() }
            .take(16)
            .ifEmpty { "learner" }
        return base + suffix
    }
}
