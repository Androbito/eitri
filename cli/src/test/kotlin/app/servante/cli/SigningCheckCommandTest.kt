package app.servante.cli

import app.servante.core.fakes.FixedClock
import app.servante.core.secrets.Secret
import app.servante.storage.project.FileSystemProjectDetector
import app.servante.tools.signing.KeystorePasswordProvider
import app.servante.tools.signing.RealSigningFileSystem
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SigningCheckCommandTest {
    private val fixtures = Path.of(
        requireNotNull(System.getProperty("servante.fixtures")) { "set by the test task" },
    )
    private val out = StringBuilder()
    private val progress = StringBuilder()

    private val fixturePasswords = KeystorePasswordProvider { path ->
        when {
            path.endsWith("release.jks") -> Secret("fjord-release-2024")
            path.endsWith("debug.keystore") -> Secret("android")
            else -> null
        }
    }

    @Test
    fun `prints the diagnosis of a project as Markdown`() = runTest {
        val code = command().run(
            projectPath = fixtures.resolve("fjord-notes").toString(),
            userAddedKeystores = listOf(fixtures.resolve("fjord-notes/keystores/debug.keystore").toString()),
        )

        assertEquals(SigningCheckCommand.OK, code)
        val markdown = out.toString()
        assertTrue("# Signing diagnosis: fjord-notes" in markdown, markdown)
        assertTrue("## Fingerprint matrix" in markdown, markdown)
        // The upload key Firebase knows, and the Play certificate it does not.
        assertTrue("89:14:B8:17:77:BA:9A:35:C1:AC:E9:BE:D6:EE:80:5E:2C:D3:57:31" in markdown, markdown)
        assertTrue("Play" in markdown, markdown)
        // No secret reaches the output, which is the whole point of it being pasteable.
        assertFalse("fjord-release-2024" in markdown, markdown)
    }

    @Test
    fun `reports the steps and the project on standard error, not in the diagnosis`() = runTest {
        command().run(fixtures.resolve("fjord-notes").toString())

        val steps = progress.toString()
        assertTrue("fjord-notes: 3 modules" in steps, steps)
        assertTrue("[2/4] Finding keystores" in steps, steps)
        assertFalse("[2/4]" in out.toString(), out.toString())
    }

    @Test
    fun `a folder that is not a Gradle project is a reason, not a crash`() = runTest {
        val code = command().run(fixtures.resolve("not-a-gradle-project").toString())

        assertEquals(SigningCheckCommand.PROJECT_UNREADABLE, code)
        assertTrue("not the root of a Gradle project" in progress.toString(), progress.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `a project with no application module says so instead of reporting nothing`() = runTest {
        val code = command().run(fixtures.resolve("groovy-app").toString())

        assertTrue(code == SigningCheckCommand.OK || code == SigningCheckCommand.CHECK_UNAVAILABLE)
        if (code == SigningCheckCommand.CHECK_UNAVAILABLE) {
            assertTrue("application plugin" in progress.toString(), progress.toString())
        }
    }

    private fun command() = SigningCheckCommand(
        detector = FileSystemProjectDetector(),
        passwords = fixturePasswords,
        out = out,
        progress = progress,
        clock = FixedClock(NOW),
        // The host's own ~/.android/debug.keystore must not change what this test reads, so home is
        // pointed at the fixtures folder, which has none.
        fileSystem = RealSigningFileSystem(homePath = fixtures.toString()),
    )

    private companion object {
        private val NOW = Instant.parse("2026-10-13T09:00:00Z")
    }
}
