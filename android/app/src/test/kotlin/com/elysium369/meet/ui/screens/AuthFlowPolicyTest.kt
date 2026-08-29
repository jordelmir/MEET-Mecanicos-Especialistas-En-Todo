package com.elysium369.meet.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFlowPolicyTest {
    @Test
    fun `normalizes email without assigning authority`() {
        assertEquals("person@example.com", AuthFormPolicy.normalizeEmail(" Person@Example.COM "))
    }

    @Test
    fun `enforces the hosted twelve character password policy`() {
        assertEquals(
            "La contraseña debe tener al menos 12 caracteres.",
            AuthFormPolicy.validatePassword("Short1!"),
        )
        assertNull(AuthFormPolicy.validatePassword("LongEnough12!"))
    }

    @Test
    fun `requires password confirmation`() {
        assertEquals(
            "Las contraseñas no coinciden.",
            AuthFormPolicy.validateConfirmation("LongEnough12!", "LongEnough13!"),
        )
    }

    @Test
    fun `recovery response never enumerates accounts`() {
        val absent = AuthFailureTranslator.userMessage(
            AuthFlowMode.RECOVERY_REQUEST,
            "User not found",
        )
        val present = AuthFailureTranslator.userMessage(
            AuthFlowMode.RECOVERY_REQUEST,
            null,
        )
        assertEquals(present, absent)
    }

    @Test
    fun `bare custom link cannot enter password recovery`() {
        assertFalse(AuthRedirectPolicy.isPasswordRecoveryLink("meet://auth/password-recovery"))
        assertFalse(
            AuthRedirectPolicy.isPasswordRecoveryLink(
                "meet://auth/password-recovery#type=recovery",
            ),
        )
        assertFalse(
            AuthRedirectPolicy.isPasswordRecoveryLink(
                "meet://auth/password-recovery#access_token=&type=recovery",
            ),
        )
        assertFalse(
            AuthRedirectPolicy.isPasswordRecoveryLink(
                "meet://auth/another-path#access_token=redacted&type=recovery",
            ),
        )
    }

    @Test
    fun `classifies invalid credentials and invalid grant correctly`() {
        assertEquals(
            AuthFailureCode.INVALID_CREDENTIALS,
            AuthFailureTranslator.classify("invalid_grant"),
        )
        assertEquals(
            AuthFailureCode.INVALID_CREDENTIALS,
            AuthFailureTranslator.classify("Invalid login credentials"),
        )
        assertEquals(
            AuthFailureCode.INVALID_CREDENTIALS,
            AuthFailureTranslator.classify("invalid_credentials"),
        )
    }

    @Test
    fun `classifies true network errors without false positive on other words`() {
        assertEquals(
            AuthFailureCode.NETWORK,
            AuthFailureTranslator.classify("java.net.UnknownHostException: Unable to resolve host"),
        )
        assertEquals(
            AuthFailureCode.NETWORK,
            AuthFailureTranslator.classify("Failed to connect to /1.2.3.4:443"),
        )
    }

    @Test
    fun `supabase recovery proof can enter password recovery`() {
        assertTrue(
            AuthRedirectPolicy.isPasswordRecoveryLink(
                "meet://auth/password-recovery#access_token=redacted&type=recovery",
            ),
        )
    }
}
