package app.servante.storage.secrets

import app.servante.core.Availability
import app.servante.core.platform.OperatingSystem
import app.servante.core.process.ProcessRunner
import app.servante.core.secrets.SecretStore

/**
 * Picks the secret store for the system Servante is running on, and falls back to the session-only store
 * when the system has none that works.
 *
 * The fallback is deliberate and visible: [Result.fallbackReason] carries why, the Settings screen shows
 * it, and nothing is written to a file. The alternative, an encrypted file of our own, would mean
 * secrets on disk.
 */
public object SecretStores {
    public data class Result(public val store: SecretStore, public val fallbackReason: String?)

    public suspend fun forSystem(
        operatingSystem: OperatingSystem,
        processRunner: ProcessRunner,
        index: SecretKeyIndex,
    ): Result {
        val native = when (operatingSystem) {
            OperatingSystem.MacOs -> MacOsKeychainSecretStore(processRunner, index)
            OperatingSystem.Linux -> LinuxSecretServiceSecretStore(processRunner, index)
            OperatingSystem.Windows -> WindowsCredentialManagerSecretStore(index)
            OperatingSystem.Unknown -> null
        } ?: return Result(
            store = InMemorySecretStore(),
            fallbackReason = "Servante does not know this system's secret store.",
        )

        return when (val availability = native.availability()) {
            is Availability.Available -> Result(native, fallbackReason = null)
            is Availability.Unavailable -> Result(InMemorySecretStore(), availability.reason)
        }
    }
}
