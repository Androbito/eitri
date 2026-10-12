package app.servante.core.secrets

import app.servante.core.Availability

/**
 * What a secret is filed under.
 *
 * @param scope what kind of secret it is: `ai-provider`, `play-service-account`.
 * @param account which one: the provider name, the service account name. Never the secret itself.
 */
public data class SecretKey(public val scope: String, public val account: String) {
    init {
        require(scope.isNotBlank()) { "A secret key needs a scope." }
        require(account.isNotBlank()) { "A secret key needs an account." }
    }

    /** The single string the OS stores use as a service name. */
    public fun qualified(): String = "servante.$scope.$account"
}

/** Where secrets are kept, and whether they survive the session. */
public enum class SecretStoreKind {
    MacOsKeychain,
    WindowsCredentialManager,
    LinuxSecretService,

    /** No OS store was available. Secrets last until the app closes and are never written anywhere. */
    InMemoryForThisSession,
}

/**
 * Where API keys and service account keys live. Never a file Servante writes, never a log line.
 *
 * Keystore passwords do not come through here at all: they are asked for, used once and dropped, which
 * is why there is no "remember this password" anywhere in the designs.
 */
public interface SecretStore {
    public val kind: SecretStoreKind

    /**
     * Whether this store can be used, and if not, why. A Linux desktop with no Secret Service running is
     * the case this exists for: the UI says so and offers the session-only fallback rather than failing
     * silently or pretending the key was saved.
     */
    public suspend fun availability(): Availability<SecretStoreKind>

    public suspend fun put(key: SecretKey, secret: Secret): Availability<Unit>

    public suspend fun get(key: SecretKey): Availability<Secret>

    public suspend fun delete(key: SecretKey): Availability<Unit>

    /** The keys this store holds for Servante. Keys only: the values stay where they are. */
    public suspend fun list(scope: String): Availability<List<SecretKey>>
}
