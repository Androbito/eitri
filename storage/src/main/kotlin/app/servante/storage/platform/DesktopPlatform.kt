package app.servante.storage.platform

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.platform.OperatingSystem
import app.servante.core.platform.Platform
import app.servante.core.unavailable
import java.awt.Desktop
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * The per-system paths and the trash, for the three desktops Servante ships on.
 *
 * The system, the home directory, the environment and the existence check are all injected, so every
 * branch is exercised on any CI runner instead of only the one the test happens to run on.
 */
public class DesktopPlatform(
    override val operatingSystem: OperatingSystem = OperatingSystem.current(),
    private val homePath: String = System.getProperty("user.home").orEmpty(),
    private val environment: (String) -> String? = System::getenv,
    private val pathExists: (String) -> Boolean = { Path.of(it).exists() },
) : Platform {

    override fun homeDirectory(): String = homePath

    override fun dataDirectory(): String = when (operatingSystem) {
        OperatingSystem.MacOs -> join(homePath, "Library", "Application Support", APP_NAME)

        OperatingSystem.Windows ->
            join(environment("APPDATA") ?: join(homePath, "AppData", "Roaming"), APP_NAME)

        OperatingSystem.Linux, OperatingSystem.Unknown ->
            join(environment("XDG_DATA_HOME") ?: join(homePath, ".local", "share"), APP_NAME.lowercase())
    }

    /**
     * Moves a path to the system trash through the JDK's own desktop integration, which uses the real
     * trash on each system, so a removal stays recoverable.
     *
     * A system where the JDK cannot reach a trash, a Linux session with no desktop for example, reports
     * unavailable and removes nothing. Servante never falls back to deleting outright: the dialog
     * promised the items would stay in the trash.
     */
    override fun moveToTrash(path: String): Availability<Unit> {
        val file = File(path)
        if (!file.exists()) return unavailable("$path is not there any more.")
        val desktop = runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        }.getOrNull()
            ?: return unavailable("This system has no trash Servante can reach, so nothing was removed.")
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            return unavailable("This system has no trash Servante can reach, so nothing was removed.")
        }
        return runCatching {
            if (desktop.moveToTrash(file)) {
                Unit.available()
            } else {
                unavailable("$path could not be moved to the trash.")
            }
        }.getOrElse { unavailable("$path could not be moved to the trash: ${it.message ?: "unknown error"}") }
    }

    override fun defaultAndroidSdkPath(): Availability<String> {
        val candidate = when (operatingSystem) {
            OperatingSystem.MacOs -> join(homePath, "Library", "Android", "sdk")

            OperatingSystem.Windows ->
                join(environment("LOCALAPPDATA") ?: join(homePath, "AppData", "Local"), "Android", "Sdk")

            OperatingSystem.Linux -> join(homePath, "Android", "Sdk")

            OperatingSystem.Unknown -> return unavailable(
                "Servante does not know where the Android SDK lives on this system. Set it in Settings.",
            )
        }
        return if (pathExists(candidate)) {
            candidate.available()
        } else {
            unavailable("No Android SDK at $candidate. Set the path in Settings.")
        }
    }

    /**
     * Joins path parts with the separator of the system being described, not of the one Servante is
     * running on. In production the two are the same; in tests it is what lets a Windows path be checked
     * on a Linux runner, which is the only way the Windows branch is ever exercised in CI.
     */
    private fun join(vararg parts: String): String {
        val separator = if (operatingSystem == OperatingSystem.Windows) "\\" else "/"
        return parts.joinToString(separator) { it.trimEnd('/', '\\') }
    }

    private companion object {
        private const val APP_NAME = "Servante"
    }
}
