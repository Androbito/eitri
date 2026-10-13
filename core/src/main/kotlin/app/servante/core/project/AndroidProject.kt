package app.servante.core.project

import app.servante.core.Availability

/**
 * What a module is, as far as Servante needs to care. Read from the plugins its build file applies.
 *
 * [Unknown] is a real answer, not a failure: a module whose build file applies plugins through a
 * convention plugin cannot be classified by reading that file, and saying so is better than guessing.
 */
public enum class ModuleKind {
    Application,
    AndroidLibrary,
    Jvm,
    Unknown,
}

/**
 * One Gradle module of the user's project.
 *
 * @param path the Gradle path, `:app` or `:feature:editor`, which is what the user sees in their IDE.
 * @param directory relative to the project root. Null when the module is declared in the settings file
 *   but its directory is not where the path says it should be, usually because the settings file moves
 *   it with `project(":x").projectDir`, which Servante does not evaluate.
 */
public data class GradleModule(
    public val path: String,
    public val directory: String?,
    public val kind: ModuleKind,
    public val appliedPluginIds: Set<String> = emptySet(),
) {
    init {
        require(path.startsWith(":")) { "A Gradle module path starts with a colon, was '$path'." }
    }

    /** The last segment of the path, which is what the sidebar and pickers show. */
    public val name: String get() = path.substringAfterLast(':')
}

/** An entry of a version catalog's `[versions]`, `[libraries]` or `[plugins]` table. */
public data class CatalogPlugin(public val alias: String, public val id: String, public val version: String?)

public data class CatalogLibrary(
    public val alias: String,
    public val module: String,
    public val version: String?,
    public val versionRef: String?,
)

/**
 * A Gradle version catalog, read from `gradle/libs.versions.toml`.
 *
 * @param path relative to the project root, because a project may name it something other than `libs`.
 */
public data class VersionCatalog(
    public val path: String,
    public val versions: Map<String, String>,
    public val libraries: List<CatalogLibrary>,
    public val plugins: List<CatalogPlugin>,
) {
    /**
     * Resolves a Gradle type-safe plugin accessor to the plugin id it stands for.
     *
     * `libs.plugins.android.application` is the accessor Gradle generates for the alias
     * `android-application`, so the lookup normalises `.`, `-` and `_` the same way Gradle does.
     */
    public fun pluginIdForAccessor(accessor: String): String? {
        val wanted = accessor.removePrefix("libs.plugins.").normaliseAlias()
        return plugins.firstOrNull { it.alias.normaliseAlias() == wanted }?.id
    }

    private fun String.normaliseAlias(): String = replace('-', '.').replace('_', '.')
}

/** The parts of `local.properties` Servante reads. Everything else in the file is ignored. */
public data class LocalProperties(public val sdkDir: String?, public val otherKeys: Set<String>)

/**
 * The user's project, as read from disk. Every field that may legitimately be absent is an
 * [Availability], so a screen shows "no version catalog in this project" rather than an empty table.
 *
 * @param rootPath absolute path of the folder the user opened, kept as a string so `core` stays free of
 *   `java.nio`. The data layer owns path handling.
 */
public data class AndroidProject(
    public val rootPath: String,
    public val name: String,
    public val modules: List<GradleModule>,
    public val versionCatalog: Availability<VersionCatalog>,
    public val localProperties: Availability<LocalProperties>,
    public val settingsFileName: String,
) {
    /** Modules that produce an app. These are the ones signing, preflight and releases care about. */
    public val applicationModules: List<GradleModule>
        get() = modules.filter { it.kind == ModuleKind.Application }
}
