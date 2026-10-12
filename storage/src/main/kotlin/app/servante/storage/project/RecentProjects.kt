package app.servante.storage.project

import app.servante.storage.db.ServanteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** A project the user opened before, for the list on the welcome screen. */
public data class RecentProject(public val path: String, public val name: String, public val openedAt: Instant)

/**
 * The recent projects list.
 *
 * It holds a path and a name, which is what the welcome screen shows. A project that has been moved or
 * deleted stays in the list until the user opens it, at which point the detector reports that the folder
 * is not there: the list is a memory of what was opened, not a claim about what still exists.
 */
public class RecentProjects(
    private val database: ServanteDatabase,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    public suspend fun remember(path: String, name: String): Unit = withContext(dispatcher) {
        database.recentProjectQueries.upsert(path, name, clock.now().toEpochMilliseconds())
    }

    public suspend fun mostRecent(limit: Int = DEFAULT_LIMIT): List<RecentProject> = withContext(dispatcher) {
        require(limit > 0) { "A limit of $limit would return nothing." }
        database.recentProjectQueries.mostRecent(limit.toLong()).executeAsList().map { row ->
            RecentProject(
                path = row.path,
                name = row.name,
                openedAt = Instant.fromEpochMilliseconds(row.openedAtEpochMillis),
            )
        }
    }

    public suspend fun forget(path: String): Unit = withContext(dispatcher) {
        database.recentProjectQueries.remove(path)
    }

    private companion object {
        private const val DEFAULT_LIMIT = 8
    }
}
