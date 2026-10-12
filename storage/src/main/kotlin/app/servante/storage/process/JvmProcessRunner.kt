package app.servante.storage.process

import app.servante.core.process.ProcessCommand
import app.servante.core.process.ProcessLine
import app.servante.core.process.ProcessOutcome
import app.servante.core.process.ProcessRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The one place Servante starts another program.
 *
 * Three things it guarantees, which is why nothing else is allowed to call `ProcessBuilder`:
 * a timeout always applies, so a hung tool cannot hang the app; cancelling the coroutine destroys the
 * process tree rather than leaking it; and a secret is written to the process's standard input and
 * closed, never passed as an argument where the process table and the captured output would carry it.
 */
public class JvmProcessRunner(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ProcessRunner {

    override suspend fun run(command: ProcessCommand): ProcessOutcome = withContext(dispatcher) {
        val process = start(command) ?: return@withContext couldNotStart(command)
        try {
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outReader = process.drain(process.inputStream, stdout)
            val errReader = process.drain(process.errorStream, stderr)
            writeSecretThenClose(command, process)

            val finished = runInterruptible {
                process.waitFor(command.timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }
            // Killed before the readers are joined, not after: joining first made a timeout cost the
            // timeout plus the join of two readers that cannot end until the process is gone.
            if (!finished) {
                process.destroyForcibly().waitFor(DESTROY_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }
            outReader.join(DRAIN_JOIN_MILLIS)
            errReader.join(DRAIN_JOIN_MILLIS)
            if (!finished) {
                ProcessOutcome.TimedOut(
                    after = command.timeout,
                    stdoutSoFar = stdout.toString(),
                    stderrSoFar = stderr.toString(),
                )
            } else {
                ProcessOutcome.Completed(
                    exitCode = process.exitValue(),
                    stdout = stdout.toString(),
                    stderr = stderr.toString(),
                )
            }
        } finally {
            // Covers cancellation as well as the normal paths: an alive process here is one nobody is
            // waiting for any more.
            if (process.isAlive) process.destroyForcibly()
        }
    }

    override fun stream(command: ProcessCommand): Flow<ProcessLine> = callbackFlow {
        val process = start(command)
        if (process == null) {
            close()
            return@callbackFlow
        }
        // trySendBlocking, not trySend: the readers are plain threads, and trySend fails silently
        // once the channel buffer is full, so a collector slower than the process lost lines with no
        // error. A build log read that way is not merely incomplete, it is wrong.
        val readers = listOf(
            process.emitLines(process.inputStream, fromStandardError = false) { trySendBlocking(it) },
            process.emitLines(process.errorStream, fromStandardError = true) { trySendBlocking(it) },
        )
        writeSecretThenClose(command, process)
        val waiter = thread(name = "servante-process-wait", isDaemon = true) {
            runCatching {
                if (!process.waitFor(command.timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
                // Joined without a timeout, deliberately. A reader ends at end of stream, which
                // follows the process being gone, and the only thing it can be waiting on by this
                // point is the collector taking the lines. The fixed cap that used to be here closed
                // the flow under any collector slower than it and dropped the rest of the output: the
                // same silent loss as the trySend it replaced, one layer further out. If something
                // other than the child holds the pipe open, cancelling the flow destroys the process
                // through awaitClose, which ends the stream and releases these joins.
                readers.forEach { it.join() }
            }
            close()
        }
        awaitClose {
            if (process.isAlive) process.destroyForcibly()
            waiter.interrupt()
        }
    }.flowOn(dispatcher)

    private fun start(command: ProcessCommand): Process? {
        val builder = ProcessBuilder(listOf(command.executable) + command.arguments)
        command.workingDirectory?.let { builder.directory(File(it)) }
        builder.environment().putAll(command.environment)
        return runCatching { builder.start() }.getOrNull()
    }

    private fun couldNotStart(command: ProcessCommand): ProcessOutcome.CouldNotStart = ProcessOutcome.CouldNotStart(
        "${command.executable} could not be started. It may not be installed or not on the path.",
    )

    /**
     * Writes the secret to standard input and closes it, so the child does not wait for more. The stream
     * is closed even when there is no secret: a tool reading stdin would otherwise block forever.
     */
    private fun writeSecretThenClose(command: ProcessCommand, process: Process) {
        runCatching {
            process.outputStream.use { stream ->
                command.stdinSecret?.let { stream.write(it.reveal().toByteArray()) }
                stream.flush()
            }
        }
    }

    private fun Process.drain(stream: InputStream, into: StringBuilder): Thread =
        thread(name = "servante-process-read", isDaemon = true) {
            runCatching {
                stream.bufferedReader().forEachLine { line ->
                    synchronized(into) { into.append(line).append('\n') }
                }
            }
        }

    private fun Process.emitLines(
        stream: InputStream,
        fromStandardError: Boolean,
        emit: (ProcessLine) -> Unit,
    ): Thread = thread(name = "servante-process-stream", isDaemon = true) {
        runCatching {
            stream.bufferedReader().forEachLine { emit(ProcessLine(it, fromStandardError)) }
        }
    }

    private companion object {
        private const val DRAIN_JOIN_MILLIS = 2_000L
        private const val DESTROY_WAIT_MILLIS = 2_000L
    }
}
