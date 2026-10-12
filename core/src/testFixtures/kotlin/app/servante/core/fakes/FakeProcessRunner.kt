package app.servante.core.fakes

import app.servante.core.process.ProcessCommand
import app.servante.core.process.ProcessLine
import app.servante.core.process.ProcessOutcome
import app.servante.core.process.ProcessRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * A process runner that starts nothing.
 *
 * It records what it was asked to run, so a test can assert on the command that was built, which is the
 * part worth testing: whether a secret stayed off the argument list, whether the arguments are the ones
 * the tool documents. The outcomes are scripted per executable.
 */
public class FakeProcessRunner(
    private val outcomes: MutableMap<String, (ProcessCommand) -> ProcessOutcome> = mutableMapOf(),
) : ProcessRunner {

    /** Every command this runner was given, in order. */
    public val commands: MutableList<ProcessCommand> = mutableListOf()

    /** What each secret written to standard input was, in order, for tests that check it arrived. */
    public val secretsWritten: MutableList<String> = mutableListOf()

    public var defaultOutcome: ProcessOutcome = ProcessOutcome.Completed(0, "", "")

    public fun onRun(executable: String, outcome: (ProcessCommand) -> ProcessOutcome) {
        outcomes[executable] = outcome
    }

    public fun succeedWith(executable: String, stdout: String) {
        onRun(executable) { ProcessOutcome.Completed(0, stdout, "") }
    }

    public fun failWith(executable: String, exitCode: Int, stderr: String) {
        onRun(executable) { ProcessOutcome.Completed(exitCode, "", stderr) }
    }

    public fun cannotStart(executable: String, reason: String = "not installed") {
        onRun(executable) { ProcessOutcome.CouldNotStart(reason) }
    }

    override suspend fun run(command: ProcessCommand): ProcessOutcome {
        commands += command
        command.stdinSecret?.let { secretsWritten += it.reveal() }
        return outcomes[command.executable]?.invoke(command) ?: defaultOutcome
    }

    override fun stream(command: ProcessCommand): Flow<ProcessLine> {
        commands += command
        command.stdinSecret?.let { secretsWritten += it.reveal() }
        val outcome = outcomes[command.executable]?.invoke(command) ?: defaultOutcome
        return when (outcome) {
            is ProcessOutcome.Completed ->
                (
                    outcome.stdout.lines().filter { it.isNotEmpty() }.map { ProcessLine(it, false) } +
                        outcome.stderr.lines().filter { it.isNotEmpty() }.map { ProcessLine(it, true) }
                    ).asFlow()

            is ProcessOutcome.TimedOut ->
                outcome.stdoutSoFar.lines().filter { it.isNotEmpty() }
                    .map { ProcessLine(it, false) }.asFlow()

            is ProcessOutcome.CouldNotStart -> emptyList<ProcessLine>().asFlow()
        }
    }

    /** The last command, for the common case of asserting on a single call. */
    public fun lastCommand(): ProcessCommand = commands.last()
}
