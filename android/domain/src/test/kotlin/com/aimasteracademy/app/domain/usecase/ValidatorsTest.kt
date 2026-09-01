package com.aimasteracademy.app.domain.usecase

import com.aimasteracademy.app.domain.util.PasswordRequirement
import com.aimasteracademy.app.domain.util.Validators
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `accepts well formed email addresses`() {
        listOf(
            "learner@example.com",
            "first.last@sub.domain.org",
            "user+tag@example.co.uk",
            "a1_b-c%d@example.io",
        ).forEach { assertThat(Validators.isValidEmail(it)).isTrue() }
    }

    @Test
    fun `rejects malformed email addresses`() {
        listOf(
            "", "  ", "no-at-sign", "@example.com", "user@", "user@domain",
            "user@@example.com", "user @example.com", "user@example.c",
        ).forEach { assertThat(Validators.isValidEmail(it)).isFalse() }
    }

    @Test
    fun `names must be a sensible length after trimming`() {
        assertThat(Validators.isValidName("Al")).isTrue()
        assertThat(Validators.isValidName("  Dependra Bas  ")).isTrue()
        assertThat(Validators.isValidName("A")).isFalse()
        assertThat(Validators.isValidName("   ")).isFalse()
        assertThat(Validators.isValidName("x".repeat(51))).isFalse()
    }

    @Test
    fun `password policy reports the first unmet requirement`() {
        assertThat(Validators.validatePassword("Ab1x")).isEqualTo(PasswordRequirement.TOO_SHORT)
        assertThat(Validators.validatePassword("12345678")).isEqualTo(PasswordRequirement.NEEDS_LETTER)
        assertThat(Validators.validatePassword("abcdefgh")).isEqualTo(PasswordRequirement.NEEDS_DIGIT)
        assertThat(Validators.validatePassword("password1")).isEqualTo(PasswordRequirement.TOO_COMMON)
        assertThat(Validators.validatePassword("Neural9Net")).isNull()
    }

    @Test
    fun `strength scoring rewards length and variety`() {
        assertThat(Validators.passwordStrength("")).isEqualTo(0)
        assertThat(Validators.passwordStrength("password1")).isEqualTo(0)
        assertThat(Validators.passwordStrength("abc12345"))
            .isLessThan(Validators.passwordStrength("Abcdef123456!"))
        assertThat(Validators.passwordStrength("Abcdef123456!")).isEqualTo(4)
    }

    @Test
    fun `username suggestions strip punctuation and fall back sensibly`() {
        assertThat(Validators.suggestUsername("Dependra Bas")).isEqualTo("dependrabas")
        assertThat(Validators.suggestUsername("A.B-C")).isEqualTo("abc")
        assertThat(Validators.suggestUsername("!!!")).isEqualTo("learner")
        assertThat(Validators.suggestUsername("Dependra", suffix = "42")).isEqualTo("dependra42")
    }
}
