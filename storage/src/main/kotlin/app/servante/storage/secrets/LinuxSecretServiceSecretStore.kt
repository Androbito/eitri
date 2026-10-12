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
 * Secrets in the Linux Secret Service, through libsecret's `secret-tool`.
 *
 * `secret-tool store` reads the secret from standard input by design, which is what Servante needs.
 * A desktop with no Secret Service running, or without libsecret installed, has nowhere to keep a
 * secret: the store reports unavailable with the reason, the UI says so, and the caller falls back to
 * the session-only store rather than writing a key to a file.
 */
public class LinuxSecretServiceSecretStore(
    private val processRunner: ProcessRunner,
    private val index: SecretKeyIndex,
) : SecretStore {

    override val kind: SecretStoreKind = SecretStoreKind.LinuxSecretService

    override suspend fun availability(): Availability<SecretStoreKind> {
        val outcome = processRunner.run(
            ProcessCommand(SECRET_TOOL, listOf("--version"), timeout = SHORT_TIMEOUT),
        )
        return if (outcome is ProcessOutcome.CouldNotStart) {
            unavailable(
                "No secret-tool on this system, so there is no Secret Service to keep keys in. " +
                    "Install libsecret-tools, or Servante will keep keys in memory for this session only.",
            )
        } else {
            kind.available()
        }
    }

    override suspend fun put(key: SecretKey, secret: Secret): Availability<Unit> {
        val outcome = processRunner.run(
            ProcessCommand(
                SECRET_TOOL,
                listOf(
                    "store",
                    "--label=Servante: ${key.scope} ${key.account}",
                    "service",
                    key.qualified(),
                    "account",
                    key.account,
                ),
                stdinSecret = secret,
                timeout = TIMEOUT,
            ),
        )
        return if (outcome is ProcessOutcome.Completed && outcome.succeeded) {
            index.add(key)
            Unit.available()
        } else {
            unavailable(describeFailure(outcome, "save ${key.account} to the Secret Service"))
        }
    }

    override suspend fun get(key: SecretKey): Availability<Secret> {
        val outcome = processRunner.run(
            ProcessCommand(
                SECRET_TOOL,
                listOf("lookup", "service", key.qualified(), "account", key.account),
                timeout = TIMEOUT,
            ),
        )
        return if (outcome is ProcessOutcome.Completed && outcome.succeeded) {
            Secret(outcome.stdout.trimEnd('\n', '\r')).available()
        } else {
            unavailable("No Secret Service entry for ${key.account} in ${key.scope}.")
        }
    }

    override suspend fun delete(key: SecretKey): Availability<Unit> {
        val outcome = processRunner.run(
            ProcessCommand(
                SECRET_TOOL,
                listOf("clear", "service", key.qualified(), "account", key.account),
                timeout = TIMEOUT,
            ),
        )
        index.remove(key)
        return if (outcome is ProcessOutcome.Completed) {
            Unit.available()
        } else {
            unavailable(describeFailure(outcome, "remove ${key.account} from the Secret Service"))
        }
    }

    override suspend fun list(scope: String): Availability<List<SecretKey>> = index.accounts(scope).available()

    private companion object {
        private const val SECRET_TOOL = "secret-tool"
        private val TIMEOUT = 20.seconds
        private val SHORT_TIMEOUT = 5.seconds
    }
}
