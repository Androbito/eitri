package app.servante.core.process

import app.servante.core.secrets.Secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behaviours:
 * - a command needs an executable
 * - a command needs a positive timeout, because a process Servante cannot stop is a hung app
 * - a command's description does not contain the secret it passes on stdin
 * - the description is the command as a reader would type it
 */
class ProcessCommandTest {
    @Test
    fun `a command needs an executable`() {
        val thrown = runCatching { ProcessCommand(executable = " ", arguments = emptyList()) }
            .exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `a command needs a positive timeout`() {
        val thrown = runCatching {
            ProcessCommand(executable = "keytool", arguments = emptyList(), timeout = 0.seconds)
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `the description does not contain the secret passed on stdin`() {
        val command = ProcessCommand(
            executable = "security",
            arguments = listOf("add-generic-password", "-s", "servante", "-a", "anthropic", "-w"),
            stdinSecret = Secret("sk-ant-notreal"),
        )

        assertFalse("sk-ant-notreal" in command.describe())
        assertFalse("sk-ant-notreal" in command.toString())
    }

    @Test
    fun `the description reads as the command a person would type`() {
        val command = ProcessCommand(
            executable = "keytool",
            arguments = listOf("-list", "-v", "-keystore", "release.jks"),
        )

        assertEquals("keytool -list -v -keystore release.jks", command.describe())
    }
}
