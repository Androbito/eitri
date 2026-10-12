package app.servante.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - a location with a line renders as path:line, the form the designs show in the mono face
 * - a location with no line renders as the path alone
 * - a location with a line and a column renders as path:line:column
 * - a finding refuses a blank title, because a finding the user cannot read is worse than none
 * - evidence is kept in the order it was gathered
 */
class FindingTest {
    @Test
    fun `a location with a line renders as path and line`() {
        assertEquals("app/build.gradle.kts:42", SourceLocation("app/build.gradle.kts", line = 42).render())
    }

    @Test
    fun `a location with no line renders as the path alone`() {
        assertEquals("google-services.json", SourceLocation("google-services.json").render())
    }

    @Test
    fun `a location with a line and a column renders all three`() {
        assertEquals("NoteRow.kt:31:18", SourceLocation("NoteRow.kt", line = 31, column = 18).render())
    }

    @Test
    fun `a finding refuses a blank title`() {
        val thrown = runCatching {
            Finding(
                id = FindingId("signing.blank"),
                severity = Severity.Warning,
                title = " ",
                explanation = "something",
            )
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `a finding keeps its evidence in order`() {
        val finding = Finding(
            id = FindingId("signing.password-committed"),
            severity = Severity.Warning,
            title = "Keystore password committed to the repository",
            explanation = "Anyone with read access to the repository can sign a build as you.",
            location = SourceLocation("app/build.gradle.kts", line = 42),
            evidence = listOf(Evidence("storePassword", "fjo…024"), Evidence("keyPassword", "fjo…024")),
            standards = listOf(StandardRef("CWE-798")),
        )

        assertEquals(listOf("storePassword", "keyPassword"), finding.evidence.map { it.label })
        assertEquals("app/build.gradle.kts:42", finding.location?.render())
    }
}
