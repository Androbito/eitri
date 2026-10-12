package app.servante.storage.secrets

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.process.ProcessCommand
import app.servante.core.process.ProcessOutcome
import app.servante.core.process.ProcessRunner
import app.servante.core.secrets.Secret
import app.servante.core.secrets.SecretKey
import app.servante.core.secrets.SecretStore
import app.servante.core.secrets.SecretStoreKind
import app.servante.core.unavailable
import kotlin.time.Duration.Companion.seconds

/**
 * Secrets in the macOS keychain, through `/usr/bin/security`.
 *
 * Writing goes through `security -i`, the tool's interactive mode, which reads its commands from
 * standard input. That is the reason it is used rather than the simpler one-shot form: with `-w` on the
 * command line the password would sit in the argument list, visible in the process table to every other
 * process on the machine. Reading needs no such care, since the secret comes back on standard output.
 */
public class MacOsKeychainSecretStore(private val processRunner: ProcessRunner, private val index: SecretKeyIndex) :
    SecretStore {

    override val kind: SecretStoreKind = SecretStoreKind.MacOsKeychain

    override suspend fun availability(): Availability<SecretStoreKind> {
        val outcome = processRunner.run(
            ProcessCommand(SECURITY, listOf("help"), timeout = SHORT_TIMEOUT),
        )
        return if (outcome is ProcessOutcome.CouldNotStart) {
            unavailable("The macOS keychain is not reachable: $SECURITY could not be run.")
        } else {
            kind.available()
        }
    }

    override suspend fun put(key: SecretKey, secret: Secret): Availability<Unit> {
        // -U updates an existing item instead of failing, so saving a new key twice is not an error.
        val script = buildString {
            append("add-generic-password -U")
            append(" -s ").append(quote(key.qualified()))
            append(" -a ").append(quote(key.account))
            append(" -w ").append(quote(secret.reveal()))
            append('\n')
        }
        val outcome = processRunner.run(
            ProcessCommand(SECURITY, listOf("-i"), stdinSecret = Secret(script), timeout = TIMEOUT),
        )
        return when {
            outcome is ProcessOutcome.Completed && outcome.succeeded -> {
                index.add(key)
                Unit.available()
            }

            else -> unavailable(describeFailure(outcome, "save ${key.account} to the macOS keychain"))
        }
    }

    override suspend fun get(key: SecretKey): Availability<Secret> {
        val outcome = processRunner.run(
            ProcessCommand(
                SECURITY,
                listOf("find-generic-password", "-s", key.qualified(), "-a", key.account, "-w"),
                timeout = TIMEOUT,
            ),
        )
        return if (outcome is ProcessOutcome.Completed && outcome.succeeded) {
            Secret(outcome.stdout.trimEnd('\n', '\r')).available()
        } else {
            unavailable("No keychain entry for ${key.account} in ${key.scope}.")
        }
    }

    override suspend fun delete(key: SecretKey): Availability<Unit> {
        val outcome = processRunner.run(
            ProcessCommand(
                SECURITY,
                listOf("delete-generic-password", "-s", key.qualified(), "-a", key.account),
                timeout = TIMEOUT,
            ),
        )
        index.remove(key)
        return if (outcome is ProcessOutcome.Completed) {
            // A missing item is not a failure: the caller asked for it to be gone and it is.
            Unit.available()
        } else {
            unavailable(describeFailure(outcome, "remove ${key.account} from the macOS keychain"))
        }
    }

    override suspend fun list(scope: String): Availability<List<SecretKey>> = index.accounts(scope).available()

    /** Quotes a value for `security -i`, which splits its input the way a shell would. */
    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        private const val SECURITY = "/usr/bin/security"
        private val TIMEOUT = 20.seconds
        private val SHORT_TIMEOUT = 5.seconds
    }
}

/**
 * Turns a process outcome into a sentence for the user, never including the output itself: a failing
 * secret tool can echo part of what it was given.
 */
internal fun describeFailure(outcome: ProcessOutcome, what: String): String = when (outcome) {
    is ProcessOutcome.CouldNotStart -> "Could not $what: ${outcome.reason}"
    is ProcessOutcome.TimedOut -> "Could not $what: the system store did not answer within ${outcome.after}."
    is ProcessOutcome.Completed -> "Could not $what: the system store returned ${outcome.exitCode}."
}
