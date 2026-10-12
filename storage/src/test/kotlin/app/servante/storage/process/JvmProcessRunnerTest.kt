package app.servante.storage.process

import app.servante.core.process.ProcessCommand
import app.servante.core.process.ProcessOutcome
import app.servante.core.secrets.Secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Starts real processes, using the JVM the tests run on so the same test passes on all three systems.
 *
 * Behaviours:
 * - a program that succeeds returns its exit code and both streams
 * - a program that fails returns its exit code rather than throwing
 * - a program that never ends is stopped at the timeout and what it printed so far is kept
 * - an executable that does not exist reports that it could not be started
 * - a secret reaches the program on standard input and is not in the command's description
 * - standard input is closed even with no secret, so a program reading it does not hang
 * - streaming emits lines as they arrive, tagged by which stream they came from
 */
class JvmProcessRunnerTest {
    private val runner = JvmProcessRunner(Dispatchers.IO)

    private fun command(
        mainClass: String,
        vararg programArguments: String,
        timeout: kotlin.time.Duration = 30.seconds,
        secret: Secret? = null,
    ) = ProcessCommand(
        executable = ProcessTestPrograms.javaExecutable,
        arguments = ProcessTestPrograms.arguments(mainClass, *programArguments),
        timeout = timeout,
        stdinSecret = secret,
    )

    @Test
    fun `a program that succeeds returns its output`() = runTest {
        val outcome = runner.run(command(PrintsOutput::class.java.name, "hello", "world"))

        assertTrue(outcome is ProcessOutcome.Completed, "was $outcome")
        assertEquals(0, outcome.exitCode)
        assertTrue(outcome.succeeded)
        assertEquals(listOf("hello", "world"), outcome.stdout.trim().lines())
        assertTrue("on stderr" in outcome.stderr)
    }

    @Test
    fun `a program that fails returns its exit code`() = runTest {
        val outcome = runner.run(command(ExitsWithCode::class.java.name, "3"))

        assertTrue(outcome is ProcessOutcome.Completed, "was $outcome")
        assertEquals(3, outcome.exitCode)
        assertFalse(outcome.succeeded)
    }

    @Test
    fun `a program that never ends is stopped at the timeout`() = runTest {
        val outcome = runner.run(command(RunsForever::class.java.name, timeout = 300.milliseconds))

        assertTrue(outcome is ProcessOutcome.TimedOut, "was $outcome")
        assertEquals(300.milliseconds, outcome.after)
    }

    @Test
    fun `an executable that does not exist reports that it could not start`() = runTest {
        val outcome = runner.run(
            ProcessCommand(executable = "servante-no-such-program", arguments = emptyList()),
        )

        assertTrue(outcome is ProcessOutcome.CouldNotStart, "was $outcome")
        assertTrue("servante-no-such-program" in outcome.reason)
    }

    @Test
    fun `a secret reaches the program on standard input and not in its description`() = runTest {
        val secret = Secret("fjord-release-2024")
        val cmd = command(EchoesStandardInput::class.java.name, secret = secret)

        val outcome = runner.run(cmd)

        assertTrue(outcome is ProcessOutcome.Completed, "was $outcome")
        assertEquals("fjord-release-2024", outcome.stdout.trim())
        assertFalse("fjord-release-2024" in cmd.describe())
    }

    @Test
    fun `standard input is closed when there is no secret`() = runTest {
        val outcome = runner.run(command(EchoesStandardInput::class.java.name, timeout = 10.seconds))

        assertTrue(outcome is ProcessOutcome.Completed, "a program reading stdin must not hang: $outcome")
        assertEquals("", outcome.stdout.trim())
    }

    @Test
    fun `streaming emits the lines a program prints`() = runTest {
        val lines = runner.stream(command(PrintsThreeLinesSlowly::class.java.name)).toList()

        assertEquals(
            listOf("line 0", "line 1", "line 2"),
            lines.filterNot { it.fromStandardError }.map { it.text },
        )
    }

    @Test
    fun `streaming tags standard error separately`() = runTest {
        val lines = runner.stream(command(PrintsOutput::class.java.name, "out")).toList()

        // Asserted as containment rather than equality: a JVM started with JAVA_TOOL_OPTIONS set, which
        // is the case in some CI images, writes its own notice to standard error first.
        assertTrue(lines.filter { it.fromStandardError }.map { it.text }.contains("on stderr"))
        assertEquals(listOf("out"), lines.filterNot { it.fromStandardError }.map { it.text })
    }
}

/**
 * A collector slower than the process used to lose lines, because the readers used `trySend` and a full
 * channel buffer fails that call silently. For a tool that parses a build log, missing lines are not an
 * incomplete answer, they are a wrong one.
 */
class JvmProcessRunnerStreamBackPressureTest {
    @Test
    fun `every line reaches a slow collector`() = runTest {
        val runner = JvmProcessRunner(Dispatchers.IO)
        val lineCount = 500
        val command = ProcessCommand(
            executable = ProcessTestPrograms.javaExecutable,
            arguments = ProcessTestPrograms.arguments(
                PrintsManyLines::class.java.name,
                lineCount.toString(),
            ),
            timeout = 60.seconds,
        )

        val received = runner.stream(command)
            .filterNot { it.fromStandardError }
            .onEach { Thread.sleep(1) }
            .toList()

        assertEquals(lineCount, received.size, "lost ${lineCount - received.size} lines")
        assertEquals((0 until lineCount).map { "line $it" }, received.map { it.text })
    }
}
