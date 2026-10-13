package app.servante.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviours:
 * - the three systems Servante ships on are recognised from their usual os.name values
 * - anything else is Unknown rather than assumed to be Linux
 */
class OperatingSystemTest {
    @Test
    fun `recognises the systems Servante ships on`() {
        assertEquals(OperatingSystem.MacOs, OperatingSystem.current("Mac OS X"))
        assertEquals(OperatingSystem.Windows, OperatingSystem.current("Windows 11"))
        assertEquals(OperatingSystem.Linux, OperatingSystem.current("Linux"))
    }

    @Test
    fun `an unrecognised system is Unknown`() {
        assertEquals(OperatingSystem.Unknown, OperatingSystem.current("Haiku"))
        assertEquals(OperatingSystem.Unknown, OperatingSystem.current(""))
    }
}
