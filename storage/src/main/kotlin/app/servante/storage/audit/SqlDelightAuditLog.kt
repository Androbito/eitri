package app.servante.storage.audit

import app.servante.core.ToolId
import app.servante.core.audit.AuditEvent
import app.servante.core.audit.AuditLog
import app.servante.core.audit.AuditOutcome
import app.servante.core.audit.ChangeKind
import app.servante.core.audit.RecordedChange
import app.servante.storage.db.ServanteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * The audit log, in SQLite.
 *
 * Append only by construction: there is no update and no delete query in `AuditEvent.sq`, so a write
 * Servante recorded cannot be quietly rewritten later. An event and its changes are inserted in one
 * transaction, so a crash between the two cannot leave an event with no changes.
 *
 * @param dispatcher injected so tests control it rather than waiting on real IO.
 */
public class SqlDelightAuditLog(
    private val database: ServanteDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuditLog {

    override suspend fun record(event: AuditEvent): Long = withContext(dispatcher) {
        database.transactionWithResult {
            database.auditEventQueries.insertEvent(
                atEpochMillis = event.at.toEpochMilliseconds(),
                tool = event.tool.key,
                action = event.action,
                summary = event.summary,
                projectPath = event.projectPath,
                outcome = event.outcome.name,
                failureReason = event.failureReason,
            )
            val id = database.auditEventQueries.lastInsertedEventId().executeAsOne()
            event.changes.forEach { change ->
                database.auditEventQueries.insertChange(
                    eventId = id,
                    path = change.path,
                    kind = change.kind.name,
                    detail = change.detail,
                )
            }
            id
        }
    }

    override suspend fun recent(limit: Int, projectPath: String?): List<AuditEvent> = withContext(dispatcher) {
        require(limit > 0) { "A limit of $limit would return nothing." }
        val rows = if (projectPath == null) {
            database.auditEventQueries.recentEvents(limit.toLong()).executeAsList()
        } else {
            database.auditEventQueries.recentEventsForProject(projectPath, limit.toLong())
                .executeAsList()
        }
        if (rows.isEmpty()) return@withContext emptyList()
        val changes = database.auditEventQueries
            .changesForEvents(rows.map { it.id })
            .executeAsList()
            .groupBy { it.eventId }
        rows.map { row ->
            AuditEvent(
                id = row.id,
                at = Instant.fromEpochMilliseconds(row.atEpochMillis),
                tool = ToolId.byKey(row.tool) ?: error("Unknown tool '${row.tool}' in the audit log."),
                action = row.action,
                summary = row.summary,
                projectPath = row.projectPath,
                changes = changes[row.id].orEmpty().map { change ->
                    RecordedChange(
                        path = change.path,
                        kind = ChangeKind.valueOf(change.kind),
                        detail = change.detail,
                    )
                },
                outcome = AuditOutcome.valueOf(row.outcome),
                failureReason = row.failureReason,
            )
        }
    }
}
