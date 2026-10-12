package app.servante.storage.project

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.flatMap
import app.servante.core.map
import app.servante.core.project.AndroidProject
import app.servante.core.project.GradleModule
import app.servante.core.project.LocalProperties
import app.servante.core.project.ModuleKind
import app.servante.core.project.ProjectDetector
import app.servante.core.project.VersionCatalog
import app.servante.core.unavailable
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Reads a folder from disk and reports the Gradle project in it.
 *
 * Nothing here runs Gradle. A folder that is not a Gradle project, a module whose directory is not where
 * its path says, a project with no version catalog: each is reported as unavailable with the reason,
 * because a tool that shows an empty module list and a tool that shows "this is not a Gradle project"
 * must not look the same.
 */
public class FileSystemProjectDetector(
    private val versionCatalogReader: VersionCatalogReader = VersionCatalogReader(),
) : ProjectDetector {

    override fun detect(rootPath: String): Availability<AndroidProject> = resolveRoot(rootPath).flatMap { root ->
        findSettingsFile(root).flatMap { settingsFile ->
            readText(settingsFile).map { script -> read(root, settingsFile, script) }
        }
    }

    private fun resolveRoot(rootPath: String): Availability<Path> {
        val root = runCatching { Path.of(rootPath) }
            .getOrElse { return unavailable("'$rootPath' is not a usable path: ${it.messageOrType()}") }
        return if (root.isDirectory()) root.available() else unavailable("'$rootPath' is not a folder.")
    }

    private fun findSettingsFile(root: Path): Availability<Path> =
        SETTINGS_FILE_NAMES.map(root::resolve).firstOrNull { it.isRegularFile() }?.available()
            ?: unavailable(
                "No settings.gradle.kts or settings.gradle in ${root.name}, " +
                    "so this folder is not the root of a Gradle project.",
            )

    private fun readText(file: Path): Availability<String> = runCatching { file.readText().available() }
        .getOrElse { unavailable("${file.name} could not be read: ${it.messageOrType()}") }

    private fun read(root: Path, settingsFile: Path, script: String): AndroidProject {
        val parsed = SettingsScriptParser.parse(script)
        val catalog = versionCatalogReader.read(root, DEFAULT_CATALOG_PATH)
        return AndroidProject(
            rootPath = root.toAbsolutePath().toString(),
            name = parsed.rootProjectName ?: root.name,
            modules = parsed.modulePaths.map { readModule(root, it, catalog.valueOrNull()) },
            versionCatalog = catalog,
            localProperties = readLocalProperties(root),
            settingsFileName = settingsFile.name,
        )
    }

    private fun readModule(root: Path, path: String, catalog: VersionCatalog?): GradleModule {
        val relative = path.removePrefix(":").replace(':', '/')
        val directory = root.resolve(relative)
        if (!directory.isDirectory()) {
            return GradleModule(path = path, directory = null, kind = ModuleKind.Unknown)
        }
        val script = BUILD_FILE_NAMES.map(directory::resolve)
            .firstOrNull { it.isRegularFile() }
            ?.let { buildFile -> runCatching { buildFile.readText() }.getOrNull() }
            ?: return GradleModule(path = path, directory = relative, kind = ModuleKind.Unknown)
        val pluginIds = BuildScriptPlugins.appliedPluginIds(script, catalog)
        return GradleModule(
            path = path,
            directory = relative,
            kind = BuildScriptPlugins.kindOf(pluginIds),
            appliedPluginIds = pluginIds,
        )
    }

    /**
     * Reads `local.properties` for the SDK path. The names of the other keys are kept so the UI can say
     * what else is in the file; their values are not, because `local.properties` is one of the places
     * people put API keys and Servante does not carry those around.
     */
    private fun readLocalProperties(root: Path): Availability<LocalProperties> {
        val file = root.resolve(LOCAL_PROPERTIES)
        if (!file.isRegularFile()) {
            return unavailable("This project has no $LOCAL_PROPERTIES.")
        }
        return readText(file).map { text ->
            val keys = linkedSetOf<String>()
            var sdkDir: String? = null
            text.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') && !it.startsWith('!') }
                .forEach { line ->
                    val key = line.substringBefore('=', missingDelimiterValue = "").trim()
                    if (key.isNotEmpty()) {
                        keys += key
                        if (key == SDK_DIR_KEY) sdkDir = line.substringAfter('=').trim()
                    }
                }
            LocalProperties(sdkDir = sdkDir, otherKeys = keys)
        }
    }

    private companion object {
        private val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")
        private val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")
        private const val DEFAULT_CATALOG_PATH = "gradle/libs.versions.toml"
        private const val LOCAL_PROPERTIES = "local.properties"
        private const val SDK_DIR_KEY = "sdk.dir"
    }
}
