package app.servante.ui.text

import app.servante.core.Severity
import app.servante.core.ToolId
import app.servante.core.net.NetworkDestinations
import app.servante.core.settings.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The interface makes a missing string a compile error, so what is left to test is the part the compiler
 * cannot see: the lookups that go through a key, and the plural forms.
 *
 * Behaviours:
 * - every registered network destination has a reason and a timing in both languages
 * - no privacy string falls through to showing its key
 * - every tool and every severity has a name in both languages
 * - the plural forms are right at zero, one and many
 * - the two languages actually differ, so a copied file does not pass as a translation
 */
class StringsTest {
    private val languages = listOf(EnglishStrings, FrenchStrings)

    @Test
    fun `every network destination is described in both languages`() {
        languages.forEach { strings ->
            NetworkDestinations.all.forEach { destination ->
                val reason = strings.privacyReason(destination)
                val timing = strings.privacyWhen(destination)
                assertNotEquals(
                    destination.reasonKey,
                    reason,
                    "${strings.language} has no reason for ${destination.label}",
                )
                assertNotEquals(
                    destination.timingKey,
                    timing,
                    "${strings.language} has no timing for ${destination.label}",
                )
                assertTrue(strings.destinationLabel(destination).isNotBlank())
            }
        }
    }

    @Test
    fun `every tool and severity has a name in both languages`() {
        languages.forEach { strings ->
            ToolId.entries.forEach { assertTrue(strings.toolName(it).isNotBlank()) }
            Severity.entries.forEach { assertTrue(strings.severityName(it).isNotBlank()) }
        }
    }

    @Test
    fun `the plural forms are right`() {
        assertEquals("Nothing wrong.", EnglishStrings.healthyWithChecks(0))
        assertEquals("Nothing wrong. 1 check passed.", EnglishStrings.healthyWithChecks(1))
        assertEquals("Nothing wrong. 14 checks passed.", EnglishStrings.healthyWithChecks(14))
        assertEquals("Rien à signaler. 1 vérification passée.", FrenchStrings.healthyWithChecks(1))
        assertEquals("Rien à signaler. 14 vérifications passées.", FrenchStrings.healthyWithChecks(14))
    }

    @Test
    fun `the project summary pluralises the tool count`() {
        assertEquals(
            "Fjord Notes, 3 tools need attention",
            EnglishStrings.projectSummary("Fjord Notes", 3),
        )
        assertEquals(
            "Fjord Notes, 1 tool needs attention",
            EnglishStrings.projectSummary("Fjord Notes", 1),
        )
        assertEquals(
            "Fjord Notes, nothing needs attention",
            EnglishStrings.projectSummary("Fjord Notes", 0),
        )
        assertEquals("Fjord Notes, 1 outil à regarder", FrenchStrings.projectSummary("Fjord Notes", 1))
    }

    @Test
    fun `French is actually translated`() {
        assertNotEquals(EnglishStrings.overview, FrenchStrings.overview)
        assertNotEquals(EnglishStrings.settings, FrenchStrings.settings)
        assertNotEquals(EnglishStrings.welcomeHeadline, FrenchStrings.welcomeHeadline)
        // The product name is deliberately the same in both: it is a French word already.
        assertEquals(EnglishStrings.appName, FrenchStrings.appName)
    }

    @Test
    fun `an explicit language wins over the system one`() {
        assertEquals(Language.French, stringsFor(Language.French).language)
        assertEquals(Language.English, stringsFor(Language.English).language)
    }
}
