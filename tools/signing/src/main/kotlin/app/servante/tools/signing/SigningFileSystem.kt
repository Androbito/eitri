package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.unavailable
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The file access the Signing tool needs, as an interface so the tool's own logic is testable with a
 * map of paths to content and no files on disk.
 *
 * Reads only. A tool cannot write: writes go through the write gate in `core`, which only the app wires
 * up. That is enforced here by there being no write method to call.
 */
public interface SigningFileSystem {
    public fun exists(path: String): Boolean

    public fun readText(path: String): Availability<String>

    public fun homeDirectory(): String

    /** Resolves [relative] against [base], returning an absolute, normalised path. */
    public fun resolve(base: String, relative: String): String

    public fun fileName(path: String): String
}

public class RealSigningFileSystem(private val homePath: String = System.getProperty("user.home").orEmpty()) :
    SigningFileSystem {
    override fun exists(path: String): Boolean = runCatching { Path.of(path).exists() }.getOrDefault(false)

    override fun readText(path: String): Availability<String> {
        val file = runCatching { Path.of(path) }
            .getOrElse { return unavailable("'$path' is not a usable path.") }
        if (!file.isRegularFile()) return unavailable("${file.name} is not there.")
        return runCatching { file.readText().available() }
            .getOrElse { unavailable("${file.name} could not be read: ${it.message ?: "unknown error"}") }
    }

    override fun homeDirectory(): String = homePath

    override fun resolve(base: String, relative: String): String =
        runCatching { Path.of(base).resolve(relative).normalize().toString() }.getOrDefault(relative)

    override fun fileName(path: String): String =
        runCatching { Path.of(path).name }.getOrDefault(path.substringAfterLast('/'))
}
