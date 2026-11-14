package com.chatverse.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun normalizeUsernamePreservesCaseAndRemovesUnsupportedCharacters() {
        assertEquals("ZakssX_1.Test", AuthValidation.normalizeUsername("  ZakssX_1.Test ! "))
    }

    @Test
    fun normalizeUsernamePreservesUppercaseWhenLimitingLength() {
        assertEquals("A".repeat(30), AuthValidation.normalizeUsername("A".repeat(45)))
    }

    @Test
    fun validUsernameAcceptsAllowedCharactersAndLength() {
        assertTrue(AuthValidation.isValidUsername("abc"))
        assertTrue(AuthValidation.isValidUsername("zakss_x.9"))
        assertTrue(AuthValidation.isValidUsername("Zakss_X.9"))
        assertTrue(AuthValidation.isValidUsername("a".repeat(30)))
    }

    @Test
    fun invalidUsernameRejectsShortLongOrUnsupportedValues() {
        assertFalse(AuthValidation.isValidUsername("ab"))
        assertFalse(AuthValidation.isValidUsername("a".repeat(31)))
        assertFalse(AuthValidation.isValidUsername("bad name"))
        assertTrue(AuthValidation.isValidUsername("UpperCase"))
    }

    @Test
    fun passwordLengthMustBeBetweenEightAnd128Characters() {
        assertFalse(AuthValidation.isValidPassword("1234567"))
        assertTrue(AuthValidation.isValidPassword("12345678"))
        assertTrue(AuthValidation.isValidPassword("p".repeat(128)))
        assertFalse(AuthValidation.isValidPassword("p".repeat(129)))
    }

    @Test
    fun passwordConfirmationMustMatchExactly() {
        assertTrue(AuthValidation.passwordsMatch("SecurePass123", "SecurePass123"))
        assertFalse(AuthValidation.passwordsMatch("SecurePass123", "securepass123"))
        assertFalse(AuthValidation.passwordsMatch("SecurePass123", "SecurePass124"))
    }
}
