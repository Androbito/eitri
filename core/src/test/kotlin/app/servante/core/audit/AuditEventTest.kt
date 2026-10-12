package app.servante.core.audit

import app.servante.core.ToolId
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Behaviours:
 * - an applied event is accepted without a failure reason
 * - a failed event without a reason is rejected, because "it failed" is not a history entry
 * - an event needs a summary a person can read
 */
class AuditEventTest {
    private val at = Instant.parse("2026-10-12T20:30:00Z")

    private fun event(outcome: AuditOutcome, failureReason: String? = null) = AuditEvent(
        id = null,
        at = at,
        tool = ToolId.Gradle,
        action = "update-version-catalog",
        summary = "Updated ksp from 2.2.10-2.0.2 to 2.2.20-2.0.3",
        projectPath = "/Users/aya/dev/fjord-notes",
        changes = listOf(RecordedChange("gradle/libs.versions.toml", ChangeKind.Edited)),
        outcome = outcome,
        failureReason = failureReason,
    )

    @Test
    fun `an applied event needs no failure reason`() {
        event(AuditOutcome.Applied)
    }

    @Test
    fun `a failed event without a reason is rejected`() {
        val thrown = runCatching { event(AuditOutcome.Failed) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `an event needs a readable summary`() {
        val thrown = runCatching {
            event(AuditOutcome.Applied).copy(summary = "  ")
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }
}
