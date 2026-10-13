package app.servante.storage.audit

import app.servante.core.ToolId
import app.servante.core.audit.AuditEvent
import app.servante.core.audit.AuditOutcome
import app.servante.core.audit.ChangeKind
import app.servante.core.audit.RecordedChange
import app.servante.storage.db.Database
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Runs against a real in-memory SQLite database rather than a fake, per `docs/adr/0008`: the queries are
 * generated, so what is worth testing is the schema and the mapping, and a fake would exercise neither.
 *
 * Behaviours:
 * - a recorded event comes back with the id the database gave it
 * - an event's changes come back with it, in the order they were recorded
 * - events come back most recent first
 * - events can be filtered to one project
 * - a failed event keeps its reason
 * - an event with no changes is still recorded, because a failed write that changed nothing is history
 * - a limit of zero is refused rather than silently returning nothing
 */
class SqlDelightAuditLogTest {
    private val database = Database.openInMemory()
    private val log = SqlDelightAuditLog(database, UnconfinedTestDispatcher())

    private fun event(
        at: String,
        summary: String,
        projectPath: String? = "/dev/fjord-notes",
        outcome: AuditOutcome = AuditOutcome.Applied,
        failureReason: String? = null,
        changes: List<RecordedChange> = listOf(
            RecordedChange("gradle/libs.versions.toml", ChangeKind.Edited, "ksp 2.2.10 to 2.2.20"),
        ),
    ) = AuditEvent(
        id = null,
        at = Instant.parse(at),
        tool = ToolId.Gradle,
        action = "update-version-catalog",
        summary = summary,
        projectPath = projectPath,
        changes = changes,
        outcome = outcome,
        failureReason = failureReason,
    )

    @Test
    fun `a recorded event comes back with its id and its changes`() = runTest {
        val id = log.record(
            event(
                at = "2026-10-12T20:30:00Z",
                summary = "Updated ksp",
                changes = listOf(
                    RecordedChange("gradle/libs.versions.toml", ChangeKind.Edited),
                    RecordedChange("servante-baseline.yml", ChangeKind.Created, "accepted one finding"),
                ),
            ),
        )

        val stored = log.recent(limit = 10).single()

        assertEquals(id, stored.id)
        assertEquals("Updated ksp", stored.summary)
        assertEquals(ToolId.Gradle, stored.tool)
        assertEquals(
            listOf("gradle/libs.versions.toml", "servante-baseline.yml"),
            stored.changes.map { it.path },
        )
        assertEquals(ChangeKind.Created, stored.changes[1].kind)
        assertEquals("accepted one finding", stored.changes[1].detail)
    }

    @Test
    fun `events come back most recent first`() = runTest {
        log.record(event(at = "2026-10-10T08:00:00Z", summary = "oldest"))
        log.record(event(at = "2026-10-12T08:00:00Z", summary = "newest"))
        log.record(event(at = "2026-10-11T08:00:00Z", summary = "middle"))

        assertEquals(
            listOf("newest", "middle", "oldest"),
            log.recent(limit = 10).map { it.summary },
        )
    }

    @Test
    fun `the limit is respected`() = runTest {
        repeat(5) { log.record(event(at = "2026-10-1${it}T08:00:00Z", summary = "event $it")) }

        assertEquals(2, log.recent(limit = 2).size)
    }

    @Test
    fun `events can be filtered to one project`() = runTest {
        log.record(event(at = "2026-10-12T08:00:00Z", summary = "fjord", projectPath = "/dev/fjord-notes"))
        log.record(event(at = "2026-10-12T09:00:00Z", summary = "wear", projectPath = "/dev/fjord-wear"))
        log.record(event(at = "2026-10-12T10:00:00Z", summary = "app level", projectPath = null))

        assertEquals(
            listOf("fjord"),
            log.recent(limit = 10, projectPath = "/dev/fjord-notes").map { it.summary },
        )
        assertEquals(3, log.recent(limit = 10).size)
    }

    @Test
    fun `a failed event keeps its reason`() = runTest {
        log.record(
            event(
                at = "2026-10-12T20:30:00Z",
                summary = "Could not update ksp",
                outcome = AuditOutcome.Failed,
                failureReason = "gradle/libs.versions.toml is read only",
            ),
        )

        val stored = log.recent(limit = 1).single()

        assertEquals(AuditOutcome.Failed, stored.outcome)
        assertEquals("gradle/libs.versions.toml is read only", stored.failureReason)
    }

    @Test
    fun `an event with no changes is still recorded`() = runTest {
        log.record(
            event(
                at = "2026-10-12T20:30:00Z",
                summary = "Nothing was written",
                outcome = AuditOutcome.Failed,
                failureReason = "the file disappeared before the write",
                changes = emptyList(),
            ),
        )

        val stored = log.recent(limit = 1).single()

        assertTrue(stored.changes.isEmpty())
        assertEquals("Nothing was written", stored.summary)
    }

    @Test
    fun `a limit of zero is refused`() = runTest {
        val thrown = runCatching { log.recent(limit = 0) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `an empty log returns nothing`() = runTest {
        assertEquals(emptyList(), log.recent(limit = 10))
    }
}
