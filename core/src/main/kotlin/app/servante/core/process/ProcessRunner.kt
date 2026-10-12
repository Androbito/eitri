package app.servante.core.process

import app.servante.core.secrets.Secret
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * One external process Servante wants to run.
 *
 * @param stdinSecret a secret written to the process's standard input. Secrets are never passed as
 *   arguments: arguments are visible to every other process on the machine through the process table,
 *   and they end up in captured output and crash reports. See `docs/adr/0007`.
 * @param timeout always set. A process with no timeout is an app that hangs with no way out, and the
 *   error state in the designs ("Gradle did not answer within 2 minutes") depends on having one.
 */
public data class ProcessCommand(
    public val executable: String,
    public val arguments: List<String>,
    public val workingDirectory: String? = null,
    public val timeout: Duration = 2.minutes,
    public val environment: Map<String, String> = emptyMap(),
    public val stdinSecret: Secret? = null,
) {
    init {
        require(executable.isNotBlank()) { "A command needs an executable." }
        require(timeout.isPositive()) { "A command needs a positive timeout, was $timeout." }
    }

    /** The command as a person would type it, for an error message or the audit log. Never the secret. */
    public fun describe(): String = (listOf(executable) + arguments).joinToString(" ")

    override fun toString(): String = "ProcessCommand(${describe()}, timeout=$timeout)"
}

/** What came back from a process. */
public sealed interface ProcessOutcome {
    public data class Completed(public val exitCode: Int, public val stdout: String, public val stderr: String) :
        ProcessOutcome {
        public val succeeded: Boolean get() = exitCode == 0
    }

    /** The process was still running at the timeout and was stopped. */
    public data class TimedOut(
        public val after: Duration,
        public val stdoutSoFar: String,
        public val stderrSoFar: String,
    ) : ProcessOutcome

    /** The process could not be started at all: not installed, not executable, no such directory. */
    public data class CouldNotStart(public val reason: String) : ProcessOutcome
}

/** A line of output as it arrives, so a long-running check can show progress. */
public data class ProcessLine(public val text: String, public val fromStandardError: Boolean)

/**
 * The single way Servante runs another program.
 *
 * Everything goes through here: Gradle, `keytool`, `sdkmanager`, `security`, `secret-tool`, the emulator.
 * One implementation means timeouts, cancellation and output capture are decided once, and it is the one
 * place to audit for the rule that a secret never reaches a log.
 */
public interface ProcessRunner {
    /** Runs to completion, or to the timeout. Cancelling the coroutine kills the process. */
    public suspend fun run(command: ProcessCommand): ProcessOutcome

    /**
     * Runs while emitting output as it arrives. The flow completes when the process does; the last
     * element of the collection is not the outcome, so callers that need the exit code use [run].
     */
    public fun stream(command: ProcessCommand): Flow<ProcessLine>
}
