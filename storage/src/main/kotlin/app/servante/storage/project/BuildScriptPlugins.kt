package app.servante.storage.project

import app.servante.core.project.ModuleKind
import app.servante.core.project.VersionCatalog

/**
 * Works out which plugins a module's build file applies, and what that makes the module.
 *
 * Reading the file is deliberate: applying the plugins would mean configuring the user's build, which
 * costs minutes and can fail for reasons that have nothing to do with the question being asked.
 */
public object BuildScriptPlugins {
    private const val ANDROID_APPLICATION = "com.android.application"
    private const val ANDROID_LIBRARY = "com.android.library"
    private val JVM_PLUGIN_IDS = setOf(
        "org.jetbrains.kotlin.jvm",
        "java-library",
        "java",
    )

    private val ID_CALL = Regex("""(?<![\w.])id\s*\(?\s*['"]([^'"]+)['"]""")
    private val KOTLIN_SHORTHAND = Regex("""(?<![\w.])kotlin\s*\(\s*['"]([^'"]+)['"]\s*\)""")
    private val ALIAS_CALL = Regex("""(?<![\w.])alias\s*\(\s*(libs(?:\.[\w.]+)+)\s*\)""")

    /**
     * @param catalog the project's version catalog, used to resolve `alias(libs.plugins.x.y)`. An alias
     *   the catalog does not define is left out rather than guessed at, so a module can come back
     *   [ModuleKind.Unknown] instead of mislabelled.
     */
    public fun appliedPluginIds(buildScript: String, catalog: VersionCatalog?): Set<String> {
        val ids = linkedSetOf<String>()
        ID_CALL.findAll(buildScript).forEach { ids += it.groupValues[1] }
        KOTLIN_SHORTHAND.findAll(buildScript).forEach { ids += "org.jetbrains.kotlin.${it.groupValues[1]}" }
        ALIAS_CALL.findAll(buildScript).forEach { match ->
            catalog?.pluginIdForAccessor(match.groupValues[1])?.let { ids += it }
        }
        return ids
    }

    public fun kindOf(pluginIds: Set<String>): ModuleKind = when {
        ANDROID_APPLICATION in pluginIds -> ModuleKind.Application
        ANDROID_LIBRARY in pluginIds -> ModuleKind.AndroidLibrary
        pluginIds.any { it in JVM_PLUGIN_IDS } -> ModuleKind.Jvm
        else -> ModuleKind.Unknown
    }
}
