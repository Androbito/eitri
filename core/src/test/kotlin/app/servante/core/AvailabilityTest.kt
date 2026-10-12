package app.servante.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behaviours:
 * - an available value is returned by valueOrNull
 * - an unavailable value returns null and keeps the reason it was given
 * - mapping an available value transforms it
 * - mapping an unavailable value leaves the reason untouched and never runs the transform
 * - an unavailable value cannot be built with a blank reason, because "unavailable" with no reason
 *   is the placeholder the product rules forbid
 */
class AvailabilityTest {
    @Test
    fun `an available value is returned`() {
        val availability: Availability<Int> = Availability.Available(42)

        assertEquals(42, availability.valueOrNull())
    }

    @Test
    fun `an unavailable value returns null and keeps its reason`() {
        val availability: Availability<Int> = Availability.Unavailable("No Play account is connected")

        assertNull(availability.valueOrNull())
        assertEquals("No Play account is connected", availability.reasonOrNull())
    }

    @Test
    fun `mapping an available value transforms it`() {
        val mapped = Availability.Available(21).map { it * 2 }

        assertEquals(42, mapped.valueOrNull())
    }

    @Test
    fun `mapping an unavailable value keeps the reason and does not run the transform`() {
        var ran = false

        val mapped: Availability<Int> = Availability.Unavailable<Int>("keystore is password protected")
            .map {
                ran = true
                it * 2
            }

        assertEquals("keystore is password protected", mapped.reasonOrNull())
        assertEquals(false, ran)
    }

    @Test
    fun `an unavailable value rejects a blank reason`() {
        val thrown = runCatching { Availability.Unavailable<Int>("   ") }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class, thrown?.let { it::class })
    }
}

class AvailabilityChainingTest {
    @Test
    fun `chaining two available reads yields the second value`() {
        val chained = Availability.Available("/dev/fjord-notes")
            .flatMap { Availability.Available("$it/settings.gradle.kts") }

        assertEquals("/dev/fjord-notes/settings.gradle.kts", chained.valueOrNull())
    }

    @Test
    fun `chaining keeps the reason of the step that failed`() {
        val chained: Availability<String> = Availability.Available("/dev/empty")
            .flatMap { Availability.Unavailable<String>("No settings.gradle.kts in empty") }
            .flatMap { Availability.Unavailable<String>("never reached") }

        assertEquals("No settings.gradle.kts in empty", chained.reasonOrNull())
    }

    @Test
    fun `chaining after a failure does not run the next step`() {
        var ran = false

        Availability.Unavailable<String>("not a folder").flatMap {
            ran = true
            Availability.Available(it)
        }

        assertEquals(false, ran)
    }
}
