package app.servante.storage

import app.servante.core.Availability
import app.servante.core.platform.OperatingSystem
import app.servante.storage.platform.DesktopPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Path conventions per system, computed from an injected home directory and environment so every case is
 * testable on every runner.
 *
 * Behaviours:
 * - the data directory follows each system's convention
 * - XDG_DATA_HOME is honoured on Linux when it is set
 * - APPDATA is honoured on Windows, and there is a fallback when it is not set
 * - the default SDK path is the usual one per system, reported as unavailable when it is not there
 * - an unknown operating system reports unavailable rather than guessing a path
 */
class DesktopPlatformTest {
    private fun platform(
        os: OperatingSystem,
        home: String = "/home/aya",
        environment: Map<String, String> = emptyMap(),
    ) = DesktopPlatform(
        operatingSystem = os,
        homePath = home,
        environment = environment::get,
        pathExists = { false },
    )

    @Test
    fun `the data directory follows each system's convention`() {
        assertEquals(
            "/Users/aya/Library/Application Support/Servante",
            platform(OperatingSystem.MacOs, home = "/Users/aya").dataDirectory(),
        )
        assertEquals(
            "/home/aya/.local/share/servante",
            platform(OperatingSystem.Linux).dataDirectory(),
        )
        assertEquals(
            """C:\Users\aya\AppData\Roaming\Servante""",
            platform(
                OperatingSystem.Windows,
                home = """C:\Users\aya""",
                environment = mapOf("APPDATA" to """C:\Users\aya\AppData\Roaming"""),
            ).dataDirectory(),
        )
    }

    @Test
    fun `XDG_DATA_HOME wins on Linux when it is set`() {
        val platform = platform(OperatingSystem.Linux, environment = mapOf("XDG_DATA_HOME" to "/data/xdg"))

        assertEquals("/data/xdg/servante", platform.dataDirectory())
    }

    @Test
    fun `Windows falls back to the home directory when APPDATA is not set`() {
        val platform = platform(OperatingSystem.Windows, home = """C:\Users\aya""")

        assertTrue(platform.dataDirectory().startsWith("""C:\Users\aya"""))
    }

    @Test
    fun `the default SDK path is the convention for the system when it is there`() {
        val mac = DesktopPlatform(
            operatingSystem = OperatingSystem.MacOs,
            homePath = "/Users/aya",
            environment = { null },
            pathExists = { it == "/Users/aya/Library/Android/sdk" },
        )

        assertEquals("/Users/aya/Library/Android/sdk", mac.defaultAndroidSdkPath().valueOrNull())
    }

    @Test
    fun `a default SDK path that is not there is unavailable with a reason`() {
        val result = platform(OperatingSystem.Linux).defaultAndroidSdkPath()

        assertTrue(result is Availability.Unavailable)
        assertTrue("Android" in result.reason, "the reason should name what was looked for")
    }

    @Test
    fun `an unknown system has no conventional SDK path`() {
        val result = platform(OperatingSystem.Unknown).defaultAndroidSdkPath()

        assertTrue(result is Availability.Unavailable)
    }
}
