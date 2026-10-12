package app.servante.core.platform

import app.servante.core.Availability

public enum class OperatingSystem {
    MacOs,
    Windows,
    Linux,
    Unknown,
    ;

    public companion object {
        /** Reads the current system from `os.name`. Anything unrecognised is [Unknown], not guessed. */
        public fun current(osName: String = System.getProperty("os.name").orEmpty()): OperatingSystem {
            val name = osName.lowercase()
            return when {
                name.contains("mac") || name.contains("darwin") -> MacOs
                name.contains("win") -> Windows
                name.contains("nux") || name.contains("nix") || name.contains("aix") -> Linux
                else -> Unknown
            }
        }
    }
}

/**
 * The things that differ per operating system, in one interface so the rest of the code does not branch
 * on the OS: where application data goes, how a file reaches the trash, where the Android SDK usually is.
 */
public interface Platform {
    public val operatingSystem: OperatingSystem

    /** Where Servante keeps its database and settings. Created if it does not exist. */
    public fun dataDirectory(): String

    /** Where the user's home directory is, used to shorten paths to `~/dev/fjord-notes` for display. */
    public fun homeDirectory(): String

    /**
     * Moves a path to the system trash, so a removal is recoverable.
     *
     * @return unavailable with a reason when the system has no trash Servante can reach, in which case
     *   nothing is removed. Servante does not fall back to deleting outright.
     */
    public fun moveToTrash(path: String): Availability<Unit>

    /** Where the Android SDK is by convention on this system, if it is there. */
    public fun defaultAndroidSdkPath(): Availability<String>
}
