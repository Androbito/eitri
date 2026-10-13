package app.servante.storage.secrets

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.secrets.Secret
import app.servante.core.secrets.SecretKey
import app.servante.core.secrets.SecretStore
import app.servante.core.secrets.SecretStoreKind
import app.servante.core.unavailable
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secrets in the Windows Credential Manager, through `advapi32`.
 *
 * Called natively rather than through a command line tool because Windows has no equivalent of
 * `security` or `secret-tool`: `cmdkey` can write a credential but cannot read one back, and the
 * PasswordVault API is only available to packaged applications. `CredReadW` and `CredWriteW` are the
 * documented way for a desktop application to do this, and the value never leaves the process.
 *
 * This is the one implementation that cannot be exercised on a macOS or Linux runner, so CI runs the
 * storage tests on a Windows runner as well. See `docs/adr/0007`.
 */
public class WindowsCredentialManagerSecretStore(
    private val index: SecretKeyIndex,
    private val advapi32: Advapi32Credentials = Advapi32Credentials.INSTANCE,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore {

    override val kind: SecretStoreKind = SecretStoreKind.WindowsCredentialManager

    override suspend fun availability(): Availability<SecretStoreKind> = withContext(dispatcher) {
        runCatching {
            // Reading an entry that does not exist still proves the library loaded and answers.
            advapi32.CredDeleteW(WString("servante.availability.probe"), CRED_TYPE_GENERIC, 0)
            kind.available()
        }.getOrElse {
            unavailable("The Windows Credential Manager is not reachable: ${it.message ?: "advapi32 failed"}")
        }
    }

    override suspend fun put(key: SecretKey, secret: Secret): Availability<Unit> = withContext(dispatcher) {
        val blob = secret.reveal().toByteArray(Charsets.UTF_16LE)
        val memory = Memory(blob.size.toLong().coerceAtLeast(1))
        try {
            memory.write(0, blob, 0, blob.size)
            val credential = Credential().apply {
                Type = CRED_TYPE_GENERIC
                TargetName = WString(key.qualified())
                CredentialBlobSize = blob.size
                CredentialBlob = memory
                Persist = CRED_PERSIST_LOCAL_MACHINE
                UserName = WString(key.account)
            }
            val written = runCatching { advapi32.CredWriteW(credential, 0) }.getOrElse { false }
            if (written) {
                index.add(key)
                Unit.available()
            } else {
                unavailable("Windows refused to save ${key.account}: error ${Native.getLastError()}.")
            }
        } finally {
            // The blob is zeroed before the memory is released: a freed page keeps its contents.
            memory.clear()
            blob.fill(0)
        }
    }

    override suspend fun get(key: SecretKey): Availability<Secret> = withContext(dispatcher) {
        val reference = PointerByReference()
        val read = runCatching {
            advapi32.CredReadW(WString(key.qualified()), CRED_TYPE_GENERIC, 0, reference)
        }.getOrElse { false }
        if (!read) {
            return@withContext unavailable("No stored credential for ${key.account} in ${key.scope}.")
        }
        val pointer = reference.value
        try {
            val credential = Credential(pointer)
            val size = credential.CredentialBlobSize
            if (size <= 0 || credential.CredentialBlob == null) {
                unavailable("The stored credential for ${key.account} is empty.")
            } else {
                val bytes = credential.CredentialBlob!!.getByteArray(0, size)
                val value = String(bytes, Charsets.UTF_16LE)
                bytes.fill(0)
                Secret(value).available()
            }
        } finally {
            runCatching { advapi32.CredFree(pointer) }
        }
    }

    override suspend fun delete(key: SecretKey): Availability<Unit> = withContext(dispatcher) {
        runCatching { advapi32.CredDeleteW(WString(key.qualified()), CRED_TYPE_GENERIC, 0) }
        index.remove(key)
        Unit.available()
    }

    override suspend fun list(scope: String): Availability<List<SecretKey>> = index.accounts(scope).available()

    private companion object {
        private const val CRED_TYPE_GENERIC = 1
        private const val CRED_PERSIST_LOCAL_MACHINE = 2
    }
}

/**
 * The four `advapi32` functions Servante needs. Mapped here rather than taken from a library because
 * JNA's bundled `Advapi32` does not cover the credential functions.
 */
public interface Advapi32Credentials : Library {
    @Suppress("FunctionNaming", "FunctionName")
    public fun CredWriteW(credential: Credential, flags: Int): Boolean

    @Suppress("FunctionNaming", "FunctionName")
    public fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean

    @Suppress("FunctionNaming", "FunctionName")
    public fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean

    @Suppress("FunctionNaming", "FunctionName")
    public fun CredFree(buffer: Pointer)

    public companion object {
        /**
         * Loaded lazily, so a non-Windows system can construct the class without the library being
         * there. The store's availability check is what reports that it is not usable.
         */
        public val INSTANCE: Advapi32Credentials by lazy {
            Native.load("Advapi32", Advapi32Credentials::class.java)
        }
    }
}

/**
 * `CREDENTIALW` from `wincred.h`. The field order is the structure's memory layout and must not change.
 */
@Suppress("PropertyName", "VariableNaming")
@Structure.FieldOrder(
    "Flags",
    "Type",
    "TargetName",
    "Comment",
    "LastWritten",
    "CredentialBlobSize",
    "CredentialBlob",
    "Persist",
    "AttributeCount",
    "Attributes",
    "TargetAlias",
    "UserName",
)
public open class Credential() : Structure() {
    public constructor(pointer: Pointer) : this() {
        useMemory(pointer)
        read()
    }

    @JvmField public var Flags: Int = 0

    @JvmField public var Type: Int = 0

    @JvmField public var TargetName: WString? = null

    @JvmField public var Comment: WString? = null

    @JvmField public var LastWritten: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField public var CredentialBlobSize: Int = 0

    @JvmField public var CredentialBlob: Pointer? = null

    @JvmField public var Persist: Int = 0

    @JvmField public var AttributeCount: Int = 0

    @JvmField public var Attributes: Pointer? = null

    @JvmField public var TargetAlias: WString? = null

    @JvmField public var UserName: WString? = null
}
