package app.servante.core.settings

import kotlinx.coroutines.flow.Flow

/** Which theme to use. The default follows the system, as the designs say. */
public enum class Appearance {
    FollowSystem,
    Light,
    Dark,
}

/**
 * The languages Servante ships. English and French from the first release.
 *
 * @param tag the BCP 47 tag, which is also the resource suffix.
 */
public enum class Language(public val tag: String) {
    English("en"),
    French("fr"),
    ;

    public companion object {
        /** Falls back to English for any other system language rather than showing untranslated keys. */
        public fun forTag(tag: String?): Language =
            entries.firstOrNull { tag != null && tag.startsWith(it.tag, ignoreCase = true) } ?: English
    }
}

/**
 * Everything the Settings screen controls.
 *
 * Paths are nullable rather than guessed: an empty SDK path means "Servante has not been told where it
 * is", which the SDK tool reports as unavailable instead of searching the disk and announcing a guess.
 */
public data class AppSettings(
    public val appearance: Appearance = Appearance.FollowSystem,
    public val language: Language? = null,
    public val androidSdkPath: String? = null,
    public val gradleJdkPath: String? = null,
    public val checkForUpdatesAtLaunch: Boolean = false,
) {
    public companion object {
        /**
         * The defaults a first launch starts from. The update check is off: it is a network call, and
         * every network call in Servante is something the user turned on.
         */
        public val DEFAULTS: AppSettings = AppSettings()
    }
}

public interface SettingsStore {
    public val settings: Flow<AppSettings>

    public suspend fun current(): AppSettings

    public suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings
}
