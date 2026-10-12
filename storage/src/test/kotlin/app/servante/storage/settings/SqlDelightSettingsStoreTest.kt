package app.servante.storage.settings

import app.servante.core.fakes.FixedClock
import app.servante.core.settings.Appearance
import app.servante.core.settings.Language
import app.servante.storage.db.Database
import app.servante.storage.db.ServanteDatabase
import app.servante.storage.project.RecentProjects
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Behaviours:
 * - a fresh database returns the defaults
 * - an updated setting is read back, and the flow carries it
 * - clearing a path stores no empty string, it removes the setting
 * - a stored value this version does not recognise falls back to the default
 * - the recent projects list is ordered by when each was last opened
 * - opening a project again moves it to the top rather than duplicating it
 */
class SqlDelightSettingsStoreTest {
    private val database: ServanteDatabase = Database.openInMemory()
    private val store = SqlDelightSettingsStore(database, UnconfinedTestDispatcher())

    @Test
    fun `a fresh database returns the defaults`() = runTest {
        val settings = store.current()

        assertEquals(Appearance.FollowSystem, settings.appearance)
        assertNull(settings.language)
        assertEquals(false, settings.checkForUpdatesAtLaunch)
    }

    @Test
    fun `an updated setting is read back and carried by the flow`() = runTest {
        store.update { it.copy(appearance = Appearance.Dark, language = Language.French) }

        assertEquals(Appearance.Dark, store.current().appearance)
        assertEquals(Language.French, store.settings.first().language)
        assertEquals(Appearance.Dark, SqlDelightSettingsStore(database).current().appearance)
    }

    @Test
    fun `clearing a path removes the setting rather than storing an empty value`() = runTest {
        store.update { it.copy(androidSdkPath = "/Users/aya/Library/Android/sdk") }
        store.update { it.copy(androidSdkPath = null) }

        assertNull(SqlDelightSettingsStore(database).current().androidSdkPath)
        assertTrue(database.settingQueries.get("paths.androidSdk").executeAsOneOrNull() == null)
    }

    @Test
    fun `a value this version does not recognise falls back to the default`() = runTest {
        database.settingQueries.put("appearance", "Sepia")

        assertEquals(Appearance.FollowSystem, SqlDelightSettingsStore(database).current().appearance)
    }
}

class RecentProjectsTest {
    private val database: ServanteDatabase = Database.openInMemory()
    private val clock = FixedClock()
    private val recent = RecentProjects(database, clock, UnconfinedTestDispatcher())

    @Test
    fun `projects come back most recently opened first`() = runTest {
        clock.advanceTo(Instant.parse("2026-10-01T09:00:00Z"))
        recent.remember("/dev/fjord-wear", "Fjord Wear")
        clock.advanceTo(Instant.parse("2026-10-12T09:00:00Z"))
        recent.remember("/dev/fjord-notes", "Fjord Notes")

        assertEquals(
            listOf("Fjord Notes", "Fjord Wear"),
            recent.mostRecent().map { it.name },
        )
    }

    @Test
    fun `opening a project again moves it to the top without duplicating it`() = runTest {
        clock.advanceTo(Instant.parse("2026-10-01T09:00:00Z"))
        recent.remember("/dev/fjord-notes", "Fjord Notes")
        recent.remember("/dev/fjord-wear", "Fjord Wear")
        clock.advanceTo(Instant.parse("2026-10-12T09:00:00Z"))
        recent.remember("/dev/fjord-notes", "Fjord Notes")

        val projects = recent.mostRecent()

        assertEquals(2, projects.size)
        assertEquals("/dev/fjord-notes", projects.first().path)
    }

    @Test
    fun `a forgotten project is gone`() = runTest {
        recent.remember("/dev/fjord-notes", "Fjord Notes")
        recent.forget("/dev/fjord-notes")

        assertTrue(recent.mostRecent().isEmpty())
    }

    @Test
    fun `the limit is respected`() = runTest {
        repeat(5) { index ->
            clock.advanceTo(Instant.parse("2026-10-0${index + 1}T09:00:00Z"))
            recent.remember("/dev/project-$index", "Project $index")
        }

        assertEquals(2, recent.mostRecent(limit = 2).size)
    }
}
