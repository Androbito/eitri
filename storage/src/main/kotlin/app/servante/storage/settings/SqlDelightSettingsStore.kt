package app.servante.storage.settings

import app.servante.core.settings.AppSettings
import app.servante.core.settings.Appearance
import app.servante.core.settings.Language
import app.servante.core.settings.SettingsStore
import app.servante.storage.db.ServanteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Settings in the local database, one row per key.
 *
 * A key that is not there, or holds a value this version does not recognise, falls back to the default
 * rather than failing: a settings file from a newer version must not stop the app from starting. The
 * current value is held in a flow so the theme and the language change as soon as the user picks them.
 */
public class SqlDelightSettingsStore(
    private val database: ServanteDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsStore {

    private val state = MutableStateFlow(AppSettings.DEFAULTS)
    private val writeLock = Mutex()
    private var loaded = false

    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun current(): AppSettings = withContext(dispatcher) {
        writeLock.withLock {
            if (!loaded) {
                state.value = read()
                loaded = true
            }
            state.value
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val before = current()
        return withContext(dispatcher) {
            writeLock.withLock {
                val after = transform(before)
                database.transaction {
                    put(KEY_APPEARANCE, after.appearance.name)
                    put(KEY_LANGUAGE, after.language?.name)
                    put(KEY_SDK_PATH, after.androidSdkPath)
                    put(KEY_JDK_PATH, after.gradleJdkPath)
                    put(KEY_UPDATE_CHECK, after.checkForUpdatesAtLaunch.toString())
                }
                state.value = after
                after
            }
        }
    }

    private fun read(): AppSettings {
        val stored = database.settingQueries.all().executeAsList().associate { it.key to it.value_ }
        return AppSettings(
            appearance = stored[KEY_APPEARANCE]
                ?.let { name -> Appearance.entries.firstOrNull { it.name == name } }
                ?: AppSettings.DEFAULTS.appearance,
            language = stored[KEY_LANGUAGE]?.let { name -> Language.entries.firstOrNull { it.name == name } },
            androidSdkPath = stored[KEY_SDK_PATH]?.ifBlank { null },
            gradleJdkPath = stored[KEY_JDK_PATH]?.ifBlank { null },
            checkForUpdatesAtLaunch = stored[KEY_UPDATE_CHECK].toBoolean(),
        )
    }

    private fun put(key: String, value: String?) {
        if (value == null) {
            database.settingQueries.remove(key)
        } else {
            database.settingQueries.put(key, value)
        }
    }

    private companion object {
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SDK_PATH = "paths.androidSdk"
        private const val KEY_JDK_PATH = "paths.gradleJdk"
        private const val KEY_UPDATE_CHECK = "updates.checkAtLaunch"
    }
}
