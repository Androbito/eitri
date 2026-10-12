package app.servante.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviours:
 * - a result with no findings is healthy and counts nothing
 * - a result counts its findings by severity
 * - a result's worst severity is the worst of its findings
 * - a result that only passed checks is healthy, and says how many passed
 */
class ToolResultTest {
    private fun finding(severity: Severity, id: String) = Finding(
        id = FindingId(id),
        severity = severity,
        title = "Title for $id",
        explanation = "Why $id matters.",
    )

    @Test
    fun `a result with no findings is healthy`() {
        val result = ToolResult(tool = ToolId.Signing, findings = emptyList(), checksPassed = 14)

        assertEquals(Severity.Healthy, result.worstSeverity)
        assertEquals(0, result.count(Severity.Critical))
        assertEquals(14, result.checksPassed)
    }

    @Test
    fun `a result counts its findings by severity`() {
        val result = ToolResult(
            tool = ToolId.Security,
            findings = listOf(
                finding(Severity.Critical, "a"),
                finding(Severity.Critical, "b"),
                finding(Severity.Warning, "c"),
                finding(Severity.Info, "d"),
            ),
            checksPassed = 7,
        )

        assertEquals(2, result.count(Severity.Critical))
        assertEquals(1, result.count(Severity.Warning))
        assertEquals(1, result.count(Severity.Info))
        assertEquals(0, result.count(Severity.Healthy))
    }

    @Test
    fun `a result's worst severity is the worst of its findings`() {
        val result = ToolResult(
            tool = ToolId.Gradle,
            findings = listOf(finding(Severity.Info, "a"), finding(Severity.Warning, "b")),
            checksPassed = 3,
        )

        assertEquals(Severity.Warning, result.worstSeverity)
    }
}
