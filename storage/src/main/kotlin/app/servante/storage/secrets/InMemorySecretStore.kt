package app.servante.storage.secrets

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.secrets.Secret
import app.servante.core.secrets.SecretKey
import app.servante.core.secrets.SecretStore
import app.servante.core.secrets.SecretStoreKind
import app.servante.core.unavailable
import java.util.concurrent.ConcurrentHashMap

/**
 * Secrets for this session only, in memory, never written anywhere.
 *
 * This is the fallback for a system with no usable store, which in practice means a Linux desktop with
 * no Secret Service. The user is told, in the UI, that keys will have to be entered again next time.
 * The alternative, an encrypted file, would put secrets on disk, which the product rules forbid.
 */
public class InMemorySecretStore : SecretStore {
    private val secrets = ConcurrentHashMap<SecretKey, Secret>()

    override val kind: SecretStoreKind = SecretStoreKind.InMemoryForThisSession

    override suspend fun availability(): Availability<SecretStoreKind> = kind.available()

    override suspend fun put(key: SecretKey, secret: Secret): Availability<Unit> {
        secrets[key] = secret
        return Unit.available()
    }

    override suspend fun get(key: SecretKey): Availability<Secret> =
        secrets[key]?.available() ?: unavailable("No key for ${key.account} in this session.")

    override suspend fun delete(key: SecretKey): Availability<Unit> {
        secrets.remove(key)
        return Unit.available()
    }

    override suspend fun list(scope: String): Availability<List<SecretKey>> =
        secrets.keys.filter { it.scope == scope }.sortedBy { it.account }.available()
}
