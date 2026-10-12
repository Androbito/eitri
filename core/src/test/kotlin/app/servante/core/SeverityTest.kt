package app.servante.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviours:
 * - the severities are ordered worst first, so a list of them sorts the way the UI shows them
 * - the worst of no severities is Healthy, because nothing wrong was found
 * - the worst of several severities is the most severe one
 */
class SeverityTest {
    @Test
    fun `severities are declared worst first`() {
        assertEquals(
            listOf(Severity.Critical, Severity.Warning, Severity.Info, Severity.Healthy),
            Severity.entries.toList(),
        )
    }

    @Test
    fun `the worst of no severities is healthy`() {
        assertEquals(Severity.Healthy, emptyList<Severity>().worst())
    }

    @Test
    fun `the worst of several severities is the most severe`() {
        assertEquals(Severity.Critical, listOf(Severity.Info, Severity.Critical, Severity.Warning).worst())
        assertEquals(Severity.Warning, listOf(Severity.Healthy, Severity.Warning, Severity.Info).worst())
    }
}
