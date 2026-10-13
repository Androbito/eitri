package app.servante.core.audit

import app.servante.core.ToolId
import kotlin.time.Instant

/** What happened to one path. */
public enum class ChangeKind {
    Edited,
    Created,
    MovedToTrash,
}

public data class RecordedChange(
    public val path: String,
    public val kind: ChangeKind,
    public val detail: String? = null,
)

public enum class AuditOutcome {
    Applied,
    Failed,
}

/**
 * One line of the local history: a write Servante performed, or tried to.
 *
 * Declines are not recorded. A user who cancelled a dialog did not change anything, and a history full
 * of things that did not happen is a history nobody reads.
 *
 * @param id assigned by the database. Null before the event is stored.
 * @param projectPath the project the write belongs to, or null for an app-level change such as removing
 *   a service account.
 */
public data class AuditEvent(
    public val id: Long?,
    public val at: Instant,
    public val tool: ToolId,
    public val action: String,
    public val summary: String,
    public val projectPath: String?,
    public val changes: List<RecordedChange>,
    public val outcome: AuditOutcome,
    public val failureReason: String? = null,
) {
    init {
        require(action.isNotBlank()) { "An audit event needs an action." }
        require(summary.isNotBlank()) { "An audit event needs a summary a person can read." }
        require(outcome == AuditOutcome.Applied || failureReason != null) {
            "A failed audit event has to say why it failed."
        }
    }
}

/** The local history. Append only: there is no update and no delete. */
public interface AuditLog {
    /** @return the id the event was stored under. */
    public suspend fun record(event: AuditEvent): Long

    /** Most recent first. @param projectPath null for every project. */
    public suspend fun recent(limit: Int, projectPath: String? = null): List<AuditEvent>
}
