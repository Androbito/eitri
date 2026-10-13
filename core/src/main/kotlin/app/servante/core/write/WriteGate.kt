package app.servante.core.write

import app.servante.core.ToolId

/** One file or folder change Servante proposes. The user sees every one of these before anything happens. */
public sealed interface PlannedChange {
    /** The path this change affects, relative to the project root where there is one. */
    public val path: String

    /**
     * @param newContent the whole file as it will be after the change. The gate writes this, so what the
     *   dialog previews and what lands on disk cannot differ: there is no diff to apply, and so no way
     *   for applying it to go differently than the preview suggested.
     * @param unifiedDiff the preview, computed from the current content and [newContent].
     */
    public data class EditFile(
        override val path: String,
        public val newContent: String,
        public val unifiedDiff: String,
        public val summary: String,
    ) : PlannedChange

    public data class CreateFile(override val path: String, public val content: String, public val summary: String) :
        PlannedChange

    /**
     * Moved to the system trash, never deleted outright, so a mistake is recoverable.
     *
     * @param sizeBytes shown in the dialog so the user can see what the space is being traded for.
     */
    public data class MoveToTrash(override val path: String, public val sizeBytes: Long) : PlannedChange
}

/**
 * A write Servante wants to perform, in the shape the confirmation dialog shows it.
 *
 * @param title the question the dialog asks, as in "Move 4 items to the trash?".
 * @param explanation what happens and what is recoverable.
 * @param projectPath the project this write belongs to, or null for an app-level change such as
 *   removing a service account. It is recorded with the audit event, which is the only way the history
 *   view can be filtered to one project.
 */
public data class WriteRequest(
    public val tool: ToolId,
    public val title: String,
    public val explanation: String,
    public val changes: List<PlannedChange>,
    public val projectPath: String? = null,
) {
    init {
        require(title.isNotBlank()) { "A write request needs a title." }
        require(changes.isNotEmpty()) { "A write request with no changes must not reach the user." }
    }

    public val totalSizeBytes: Long
        get() = changes.filterIsInstance<PlannedChange.MoveToTrash>().sumOf { it.sizeBytes }
}

public sealed interface WriteOutcome {
    /** The user said no. Nothing was touched and nothing is recorded as a change. */
    public data object Declined : WriteOutcome

    public data class Applied(public val auditEventId: Long, public val changed: List<String>) : WriteOutcome

    /**
     * The user confirmed and the write failed.
     *
     * @param changed what had already been written when it failed, so the user is told the truth rather
     *   than "nothing was changed" when something was.
     */
    public data class Failed(
        public val reason: String,
        public val changed: List<String>,
        public val auditEventId: Long?,
    ) : WriteOutcome
}

/**
 * The only way Servante writes anything.
 *
 * Every file edit, file creation and move to the trash goes through here. The gate shows the
 * confirmation, performs the write and records the audit event. There is no other write path, which is
 * what makes "it reads by default" a property of the code rather than a promise in a README.
 */
public interface WriteGate {
    public suspend fun request(request: WriteRequest): WriteOutcome
}

/**
 * Asks the user. Implemented by the UI, which shows the dialog, and by tests, which answer immediately.
 *
 * It is a separate interface from [WriteGate] so the gate's behaviour, write then record, can be tested
 * without a UI, and so the UI never gains the ability to write without going through the gate.
 */
public fun interface WriteConfirmer {
    /** @return true when the user confirmed. A false answer means nothing is touched. */
    public suspend fun confirm(request: WriteRequest): Boolean
}
