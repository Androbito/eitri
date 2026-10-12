package app.servante.storage.secrets

import app.servante.core.secrets.SecretKey
import app.servante.storage.db.ServanteDatabase
import kotlinx.serialization.json.Json

/**
 * Which secrets Servante has stored, by name only.
 *
 * It exists because the OS stores have no portable way to list what belongs to one application:
 * `security dump-keychain` prompts the user, and `secret-tool` has no scoped search. Rather than ask
 * the user for their keychain password to answer "which providers are configured", Servante keeps the
 * names of its own entries and looks each one up by name.
 *
 * Only names. No value of any secret is ever written here, and this index lives in the same database as
 * the audit log, which holds no secret either.
 */
public interface SecretKeyIndex {
    public suspend fun add(key: SecretKey)

    public suspend fun remove(key: SecretKey)

    public suspend fun accounts(scope: String): List<SecretKey>
}

public class DatabaseSecretKeyIndex(private val database: ServanteDatabase) : SecretKeyIndex {
    override suspend fun add(key: SecretKey) {
        val accounts = (read(key.scope) + key.account).distinct().sorted()
        write(key.scope, accounts)
    }

    override suspend fun remove(key: SecretKey) {
        write(key.scope, read(key.scope) - key.account)
    }

    override suspend fun accounts(scope: String): List<SecretKey> = read(scope).map { SecretKey(scope, it) }

    private fun read(scope: String): List<String> {
        val stored = database.settingQueries.get(settingKey(scope)).executeAsOneOrNull() ?: return emptyList()
        return runCatching { Json.decodeFromString<List<String>>(stored) }.getOrDefault(emptyList())
    }

    private fun write(scope: String, accounts: List<String>) {
        if (accounts.isEmpty()) {
            database.settingQueries.remove(settingKey(scope))
        } else {
            database.settingQueries.put(settingKey(scope), Json.encodeToString(accounts))
        }
    }

    private fun settingKey(scope: String) = "secrets.index.$scope"
}

/** Used by tests and by the session-only store, which has nothing to persist. */
public class InMemorySecretKeyIndex : SecretKeyIndex {
    private val accounts = mutableMapOf<String, MutableSet<String>>()

    override suspend fun add(key: SecretKey) {
        accounts.getOrPut(key.scope) { linkedSetOf() } += key.account
    }

    override suspend fun remove(key: SecretKey) {
        accounts[key.scope]?.remove(key.account)
    }

    override suspend fun accounts(scope: String): List<SecretKey> =
        accounts[scope].orEmpty().map { SecretKey(scope, it) }
}
