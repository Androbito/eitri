package app.servante.storage.project

import app.servante.core.Availability
import app.servante.core.project.CatalogLibrary
import app.servante.core.project.CatalogPlugin
import app.servante.core.project.VersionCatalog
import app.servante.core.unavailable
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Reads a Gradle version catalog.
 *
 * Parsing is done by a TOML parser rather than by hand, because a catalog uses inline tables, dotted
 * keys and quoted keys, and a hand-written reader gets one of them wrong quietly. Every lookup here goes
 * through a type check first: `version` is a string in one entry and a table in the next, and an entry
 * Servante cannot make sense of is skipped rather than guessed at.
 */
public class VersionCatalogReader {
    public fun read(root: Path, relativePath: String): Availability<VersionCatalog> {
        val file = root.resolve(relativePath)
        if (!file.isRegularFile()) {
            return unavailable("This project has no $relativePath.")
        }
        val parsed = runCatching { Toml.parse(file.readText()) }
            .getOrElse { return unavailable("$relativePath could not be read: ${it.messageOrType()}") }
        if (parsed.hasErrors()) {
            val first = parsed.errors().first()
            return unavailable("$relativePath is not valid TOML: ${first.toString().trim()}")
        }

        return Availability.Available(
            VersionCatalog(
                path = relativePath,
                versions = readVersions(parsed.getTable("versions")),
                libraries = readLibraries(parsed.getTable("libraries")),
                plugins = readPlugins(parsed.getTable("plugins")),
            ),
        )
    }

    /**
     * `[versions]` holds either a plain string or a rich version table. A rich version is reported by its
     * `require` or `prefer` field; one with neither has no single version to show and is left out.
     */
    private fun readVersions(table: TomlTable?): Map<String, String> {
        if (table == null) return emptyMap()
        return table.keySet().mapNotNull { key ->
            val value = table.literalString(key)
                ?: table.literalTable(key)?.let { it.literalString("require") ?: it.literalString("prefer") }
            value?.let { key to it }
        }.toMap()
    }

    private fun readLibraries(table: TomlTable?): List<CatalogLibrary> {
        if (table == null) return emptyList()
        return table.keySet().mapNotNull { alias -> readLibrary(table, alias) }
    }

    private fun readLibrary(table: TomlTable, alias: String): CatalogLibrary? =
        table.literalString(alias)?.let { shorthand -> shorthandLibrary(alias, shorthand) }
            ?: table.literalTable(alias)?.let { entry -> tableLibrary(alias, entry) }

    /** `alias = "group:name:version"`, the compact form. */
    private fun shorthandLibrary(alias: String, shorthand: String): CatalogLibrary? {
        val parts = shorthand.split(':')
        if (parts.size < MODULE_PARTS) return null
        return CatalogLibrary(
            alias = alias,
            module = "${parts[0]}:${parts[1]}",
            version = parts.getOrNull(MODULE_PARTS),
            versionRef = null,
        )
    }

    /** `alias = { module = "group:name", version.ref = "x" }`, or the `group` plus `name` form. */
    private fun tableLibrary(alias: String, entry: TomlTable): CatalogLibrary? {
        val module = entry.literalString("module") ?: entry.groupAndName() ?: return null
        return CatalogLibrary(
            alias = alias,
            module = module,
            version = entry.literalString("version"),
            versionRef = entry.versionRef(),
        )
    }

    private fun TomlTable.groupAndName(): String? {
        val group = literalString("group") ?: return null
        val name = literalString("name") ?: return null
        return "$group:$name"
    }

    private fun readPlugins(table: TomlTable?): List<CatalogPlugin> {
        if (table == null) return emptyList()
        return table.keySet().mapNotNull { alias ->
            val shorthand = table.literalString(alias)
            if (shorthand != null) {
                // `alias = "plugin.id:version"`.
                return@mapNotNull CatalogPlugin(
                    alias = alias,
                    id = shorthand.substringBefore(':'),
                    version = shorthand.substringAfter(':', missingDelimiterValue = "").ifEmpty { null },
                )
            }
            val entry = table.literalTable(alias) ?: return@mapNotNull null
            val id = entry.literalString("id") ?: return@mapNotNull null
            CatalogPlugin(
                alias = alias,
                id = id,
                version = entry.literalString("version") ?: entry.versionRef(),
            )
        }
    }

    /**
     * The version reference of an entry, written either as the dotted key `version.ref = "x"`, which TOML
     * parses as a nested table, or as the quoted literal key `"version.ref" = "x"`.
     */
    private fun TomlTable.versionRef(): String? =
        literalTable("version")?.literalString("ref") ?: literalString(VERSION_REF_LITERAL)

    /**
     * Reads a key as a string, or returns null when it holds anything else. The list form is used
     * throughout because the single-string form treats a dot in the key as a path separator, so an alias
     * containing a dot would be looked up in the wrong place.
     */
    private fun TomlTable.literalString(key: String): String? =
        if (isString(listOf(key))) getString(listOf(key)) else null

    private fun TomlTable.literalTable(key: String): TomlTable? =
        if (isTable(listOf(key))) getTable(listOf(key)) else null

    private companion object {
        private const val MODULE_PARTS = 2
        private const val VERSION_REF_LITERAL = "version.ref"
    }
}

internal fun Throwable.messageOrType(): String = message ?: this::class.simpleName ?: "unknown error"
