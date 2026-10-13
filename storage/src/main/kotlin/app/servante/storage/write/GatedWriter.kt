package app.servante.storage.write

import app.servante.core.audit.AuditEvent
import app.servante.core.audit.AuditLog
import app.servante.core.audit.AuditOutcome
import app.servante.core.audit.ChangeKind
import app.servante.core.audit.RecordedChange
import app.servante.core.platform.Platform
import app.servante.core.write.PlannedChange
import app.servante.core.write.WriteConfirmer
import app.servante.core.write.WriteGate
import app.servante.core.write.WriteOutcome
import app.servante.core.write.WriteRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock

/**
 * The only thing in Servante that writes to the user's disk.
 *
 * It asks, then writes, then records, in that order, and it stops at the first failure. Three choices
 * worth stating:
 *
 * A file is written to a temporary file next to it and moved into place, so an interrupted write cannot
 * leave the user's `libs.versions.toml` half rewritten.
 *
 * A removal goes to the system trash through the platform, never to an outright delete, because the
 * dialog told the user the items would stay in the trash.
 *
 * A declined request records nothing. A confirmed request records what happened, including the paths that
 * were already written when a later one failed, so the history never claims nothing changed when
 * something did.
 */
public class GatedWriter(
    private val confirmer: WriteConfirmer,
    private val auditLog: AuditLog,
    private val platform: Platform,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WriteGate {

    override suspend fun request(request: WriteRequest): WriteOutcome {
        if (!confirmer.confirm(request)) return WriteOutcome.Declined
        return withContext(dispatcher) { applyAndRecord(request) }
    }

    private suspend fun applyAndRecord(request: WriteRequest): WriteOutcome {
        val done = mutableListOf<RecordedChange>()
        val failure = request.changes.firstNotNullOfOrNull { change ->
            apply(change)?.also { } ?: run {
                done += change.recorded()
                null
            }
        }
        val event = AuditEvent(
            id = null,
            at = clock.now(),
            tool = request.tool,
            action = request.title,
            summary = request.explanation,
            projectPath = request.projectPath,
            changes = done,
            outcome = if (failure == null) AuditOutcome.Applied else AuditOutcome.Failed,
            failureReason = failure,
        )
        // Recorded outside cancellation. The files have already changed, so a caller that cancels
        // between the write and the record would otherwise leave a change with no history entry, and
        // "every write is recorded" would be true only when nobody closed the window at the wrong
        // moment. A recording that fails outright is reported rather than swallowed, for the same
        // reason: the user has to know the change was made and not written down.
        val eventId = withContext(NonCancellable) { runCatching { auditLog.record(event) } }
            .getOrElse { thrown ->
                return WriteOutcome.Failed(
                    reason = "The change was made but could not be recorded in the history: " +
                        (thrown.message ?: thrown::class.simpleName ?: "unknown error"),
                    changed = done.map { it.path },
                    auditEventId = null,
                )
            }
        return if (failure == null) {
            WriteOutcome.Applied(auditEventId = eventId, changed = done.map { it.path })
        } else {
            WriteOutcome.Failed(reason = failure, changed = done.map { it.path }, auditEventId = eventId)
        }
    }

    /** @return null when the change was applied, or the reason it was not. */
    private fun apply(change: PlannedChange): String? = when (change) {
        is PlannedChange.EditFile -> writeAtomically(Path.of(change.path), change.newContent)

        is PlannedChange.CreateFile -> writeAtomically(Path.of(change.path), change.content)

        is PlannedChange.MoveToTrash ->
            platform.moveToTrash(change.path).reasonOrNull()
    }

    private fun writeAtomically(target: Path, content: String): String? {
        val temporary = target.resolveSibling("${target.fileName}.servante-tmp")
        return runCatching {
            target.createParentDirectories()
            temporary.writeText(content)
            temporary.moveTo(target, StandardCopyOption.REPLACE_EXISTING)
            null
        }.getOrElse { thrown ->
            // The clean-up must not throw over the real failure: on a path whose parent is not a folder,
            // even asking whether the temporary file exists fails.
            runCatching { temporary.deleteIfExists() }
            "${target.fileName} could not be written: ${thrown.message ?: thrown::class.simpleName}"
        }
    }

    private fun PlannedChange.recorded(): RecordedChange = when (this) {
        is PlannedChange.EditFile -> RecordedChange(path, ChangeKind.Edited, summary)
        is PlannedChange.CreateFile -> RecordedChange(path, ChangeKind.Created, summary)
        is PlannedChange.MoveToTrash -> RecordedChange(path, ChangeKind.MovedToTrash)
    }
}

/** Reads a file's current content so a diff can be built against it. Null when it is not there yet. */
public fun currentContentOf(path: Path): String? = runCatching { path.readText() }.getOrNull()
