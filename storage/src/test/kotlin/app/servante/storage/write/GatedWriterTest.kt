package app.servante.storage.write

import app.servante.core.Availability
import app.servante.core.ToolId
import app.servante.core.audit.AuditOutcome
import app.servante.core.available
import app.servante.core.fakes.FixedClock
import app.servante.core.platform.OperatingSystem
import app.servante.core.platform.Platform
import app.servante.core.unavailable
import app.servante.core.write.PlannedChange
import app.servante.core.write.WriteConfirmer
import app.servante.core.write.WriteOutcome
import app.servante.core.write.WriteRequest
import app.servante.storage.audit.SqlDelightAuditLog
import app.servante.storage.db.Database
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - a declined request writes nothing and records nothing
 * - a confirmed edit writes the new content and records one event
 * - no temporary file is left behind by an atomic write
 * - a confirmed create makes the file and its parent folders
 * - a trash request goes through the platform, not through a delete
 * - a platform that cannot reach the trash leaves the file alone and records the failure
 * - a failure part way through records the paths that were already written
 */
class GatedWriterTest {
    private val database = Database.openInMemory()
    private val auditLog = SqlDelightAuditLog(database, UnconfinedTestDispatcher())
    private val clock = FixedClock()
    private lateinit var root: Path

    private class RecordingPlatform(private val trashResult: Availability<Unit> = Unit.available()) : Platform {
        val trashed: MutableList<String> = mutableListOf()

        override val operatingSystem: OperatingSystem = OperatingSystem.Linux

        override fun dataDirectory(): String = "/tmp/servante-test"

        override fun homeDirectory(): String = "/home/aya"

        override fun moveToTrash(path: String): Availability<Unit> {
            if (trashResult is Availability.Available) trashed += path
            return trashResult
        }

        override fun defaultAndroidSdkPath(): Availability<String> = unavailable("not set in tests")
    }

    private fun writer(confirm: Boolean, platform: Platform = RecordingPlatform()) = GatedWriter(
        confirmer = WriteConfirmer { confirm },
        auditLog = auditLog,
        platform = platform,
        clock = clock,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun setUpRoot(): Path {
        root = Files.createTempDirectory("servante-gate")
        return root
    }

    private fun editRequest(target: Path, content: String) = WriteRequest(
        tool = ToolId.Gradle,
        title = "Update ksp in libs.versions.toml?",
        explanation = "One file changes. Nothing is committed for you.",
        changes = listOf(
            PlannedChange.EditFile(
                path = target.toString(),
                newContent = content,
                unifiedDiff = "- old\n+ new",
                summary = "ksp 2.2.10-2.0.2 to 2.2.20-2.0.3",
            ),
        ),
    )

    @Test
    fun `a declined request writes nothing and records nothing`() = runTest {
        val target = setUpRoot().resolve("libs.versions.toml")
        target.writeText("ksp = \"2.2.10-2.0.2\"\n")

        val outcome = writer(confirm = false).request(editRequest(target, "ksp = \"2.2.20-2.0.3\"\n"))

        assertEquals(WriteOutcome.Declined, outcome)
        assertEquals("ksp = \"2.2.10-2.0.2\"\n", target.readText())
        assertTrue(auditLog.recent(limit = 10).isEmpty())
    }

    @Test
    fun `a confirmed edit writes the new content and records it`() = runTest {
        val target = setUpRoot().resolve("libs.versions.toml")
        target.writeText("ksp = \"2.2.10-2.0.2\"\n")

        val outcome = writer(confirm = true).request(editRequest(target, "ksp = \"2.2.20-2.0.3\"\n"))

        assertTrue(outcome is WriteOutcome.Applied, "was $outcome")
        assertEquals("ksp = \"2.2.20-2.0.3\"\n", target.readText())
        val event = auditLog.recent(limit = 10).single()
        assertEquals(AuditOutcome.Applied, event.outcome)
        assertEquals(outcome.auditEventId, event.id)
        assertEquals("ksp 2.2.10-2.0.2 to 2.2.20-2.0.3", event.changes.single().detail)
        assertEquals(clock.now(), event.at)
    }

    @Test
    fun `no temporary file is left behind`() = runTest {
        val target = setUpRoot().resolve("libs.versions.toml")
        target.writeText("before\n")

        writer(confirm = true).request(editRequest(target, "after\n"))

        val leftovers = Files.list(root).use { stream ->
            stream.map { it.fileName.toString() }.filter { it.contains("servante-tmp") }.toList()
        }
        assertTrue(leftovers.isEmpty(), "found $leftovers")
    }

    @Test
    fun `a confirmed create makes the file and its parent folders`() = runTest {
        val target = setUpRoot().resolve("nested/deeper/servante-baseline.yml")

        val outcome = writer(confirm = true).request(
            WriteRequest(
                tool = ToolId.Security,
                title = "Accept this finding?",
                explanation = "The reason is saved in servante-baseline.yml.",
                changes = listOf(
                    PlannedChange.CreateFile(
                        path = target.toString(),
                        content = "accepted: []\n",
                        summary = "baseline created",
                    ),
                ),
            ),
        )

        assertTrue(outcome is WriteOutcome.Applied, "was $outcome")
        assertEquals("accepted: []\n", target.readText())
    }

    @Test
    fun `a trash request goes through the platform`() = runTest {
        val platform = RecordingPlatform()
        setUpRoot()

        writer(confirm = true, platform = platform).request(
            WriteRequest(
                tool = ToolId.SdkDisk,
                title = "Move 1 item to the trash?",
                explanation = "This frees 6.2 GB.",
                changes = listOf(
                    PlannedChange.MoveToTrash("/sdk/system-images/android-33", sizeBytes = 6_200_000_000),
                ),
            ),
        )

        assertEquals(listOf("/sdk/system-images/android-33"), platform.trashed)
    }

    @Test
    fun `a system with no trash leaves the file alone and records the failure`() = runTest {
        val platform = RecordingPlatform(trashResult = unavailable("This system has no trash."))

        val outcome = writer(confirm = true, platform = platform).request(
            WriteRequest(
                tool = ToolId.SdkDisk,
                title = "Move 1 item to the trash?",
                explanation = "This frees 6.2 GB.",
                changes = listOf(PlannedChange.MoveToTrash("/sdk/old", sizeBytes = 1)),
            ),
        )

        assertTrue(outcome is WriteOutcome.Failed, "was $outcome")
        assertEquals("This system has no trash.", outcome.reason)
        assertTrue(platform.trashed.isEmpty())
        val event = auditLog.recent(limit = 10).single()
        assertEquals(AuditOutcome.Failed, event.outcome)
        assertEquals("This system has no trash.", event.failureReason)
        assertTrue(event.changes.isEmpty())
    }

    @Test
    fun `a failure part way through records what was already written`() = runTest {
        val first = setUpRoot().resolve("first.toml")
        // The parent of this path is a file, not a folder, so creating it fails the same way on every
        // system. A read-only folder would not: Windows ignores the read-only bit for the owner.
        val blocker = root.resolve("blocker.txt")
        blocker.writeText("in the way\n")
        val unwritable = blocker.resolve("nested.txt")

        val outcome = writer(confirm = true).request(
            WriteRequest(
                tool = ToolId.Gradle,
                title = "Change two files?",
                explanation = "Two files change.",
                changes = listOf(
                    PlannedChange.CreateFile(first.toString(), "written\n", "first"),
                    // A directory cannot be replaced by a file, so this one fails.
                    PlannedChange.CreateFile(unwritable.toString(), "nope\n", "second"),
                ),
            ),
        )

        assertTrue(outcome is WriteOutcome.Failed, "was $outcome")
        assertEquals(listOf(first.toString()), outcome.changed)
        assertTrue(first.exists())
        assertEquals("written\n", first.readText())
        val event = auditLog.recent(limit = 10).single()
        assertEquals(1, event.changes.size)
        assertFalse(event.failureReason.isNullOrBlank())
    }
}
