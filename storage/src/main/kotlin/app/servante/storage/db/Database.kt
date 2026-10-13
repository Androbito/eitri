package app.servante.storage.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

/**
 * Opens Servante's local database.
 *
 * One file, in the platform's data directory. It holds the audit log, settings, the recent project list
 * and cached tool results. It never holds a secret: see `docs/adr/0007`.
 *
 * The driver is given the generated schema, so it creates the tables in an empty file and migrates an
 * older one by its `user_version`. SQLDelight checks each migration against the committed schema at
 * build time, which is the reason the audit log can be promised to survive an upgrade.
 */
public object Database {
    /** @param directory the platform data directory, created if it is not there yet. */
    public fun openFile(directory: Path, fileName: String = "servante.db"): ServanteDatabase {
        directory.createDirectories()
        val file = directory.resolve(fileName)
        return open(
            JdbcSqliteDriver(
                url = "jdbc:sqlite:${file.absolutePathString()}",
                properties = Properties(),
                schema = ServanteDatabase.Schema,
            ),
        )
    }

    /** An empty database that lives only as long as the driver. Used by tests. */
    public fun openInMemory(): ServanteDatabase = open(
        JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            properties = Properties(),
            schema = ServanteDatabase.Schema,
        ),
    )

    private fun open(driver: SqlDriver): ServanteDatabase {
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
        return ServanteDatabase(driver)
    }
}
