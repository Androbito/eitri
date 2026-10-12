package app.servante.core.flags

import app.servante.core.ToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - every tool has a flag, so adding a tool without one is a compile-time gap and not a shipped surprise
 * - only shipped tools are on by default
 * - the gallery is never on by default
 * - the enabled tool list is in sidebar order
 */
class FeatureFlagsTest {
    @Test
    fun `every tool has a flag`() {
        val covered = Feature.entries.mapNotNull { it.tool }.toSet()

        assertEquals(ToolId.entries.toSet(), covered)
    }

    @Test
    fun `only shipped tools are on by default`() {
        assertTrue(FeatureFlags.Default.isEnabled(Feature.Signing))
        assertFalse(FeatureFlags.Default.isEnabled(Feature.Compose))
        assertFalse(FeatureFlags.Default.isEnabled(Feature.Security))
    }

    @Test
    fun `the component gallery is never on by default`() {
        assertFalse(FeatureFlags.Default.isEnabled(Feature.ComponentGallery))
        assertTrue(FeatureFlags.AllEnabled.isEnabled(Feature.ComponentGallery))
    }

    @Test
    fun `the enabled tools are in sidebar order`() {
        val flags = FeatureFlags.of(Feature.Security, Feature.Signing, Feature.Gradle)

        assertEquals(listOf(ToolId.Signing, ToolId.Gradle, ToolId.Security), flags.enabledTools())
    }

    @Test
    fun `by default the only tool shown is the one that has shipped`() {
        assertEquals(listOf(ToolId.Signing), FeatureFlags.Default.enabledTools())
    }
}
