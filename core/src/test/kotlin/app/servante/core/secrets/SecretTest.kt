package app.servante.core.secrets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The product rule this type carries: secrets never touch disk or logs. A secret that prints itself in a
 * stack trace or a captured process output has already broken it, so the only way to see the value is to
 * ask for it by name.
 *
 * Behaviours:
 * - a secret does not print its value
 * - a secret printed inside another object does not print its value either
 * - reveal returns the value
 * - a secret can be compared without being printed
 */
class SecretTest {
    @Test
    fun `a secret does not print its value`() {
        val secret = Secret("fjord-release-2024")

        assertFalse("fjord-release-2024" in secret.toString())
        assertEquals("Secret(hidden)", secret.toString())
    }

    @Test
    fun `a secret inside another object does not print its value`() {
        data class Holder(val name: String, val password: Secret)

        val printed = Holder("release.jks", Secret("fjord-release-2024")).toString()

        assertFalse("fjord-release-2024" in printed)
        assertTrue("release.jks" in printed)
    }

    @Test
    fun `reveal returns the value`() {
        assertEquals("fjord-release-2024", Secret("fjord-release-2024").reveal())
    }

    @Test
    fun `secrets with the same value are equal`() {
        assertEquals(Secret("a"), Secret("a"))
        assertFalse(Secret("a") == Secret("b"))
    }

    @Test
    fun `masking shows the ends of a value and never the middle`() {
        assertEquals("AIza…0000", mask("AIzaSyDEMOkeyNotRealFjordNotes0000"))
    }

    @Test
    fun `masking a short value shows nothing of it`() {
        assertEquals("………", mask("abcdefg"))
        assertEquals("………", mask(""))
    }
}
