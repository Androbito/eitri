package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.secrets.Secret
import app.servante.core.unavailable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.ECKey
import java.security.interfaces.RSAKey
import javax.security.auth.x500.X500Principal
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.time.Instant

/**
 * Reads a keystore and the certificates in it.
 *
 * The JDK does this, so Servante does not shell out to `keytool`: the fingerprints are computed over the
 * DER encoding of each certificate, which is the same thing `keytool` prints, and no password crosses a
 * process boundary.
 *
 * The password is used once and dropped. It is never stored, never logged, and never put in a message:
 * a wrong-password error names the file, not the password.
 */
public class KeystoreReader(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    /**
     * @param password the keystore password, which is also tried as the key password. A keystore whose
     *   key password differs from the store password reports the aliases it could open and leaves the
     *   rest unavailable, rather than failing as a whole.
     */
    public suspend fun read(path: String, password: Secret): Availability<Keystore> = withContext(dispatcher) {
        val file = Path.of(path)
        if (!file.isRegularFile()) {
            return@withContext unavailable("${file.name} is not there.")
        }
        val declaredFormat = detectFormat(file)
        // The provider is tried rather than chosen from the extension or from the magic bytes: this
        // JDK's PKCS12 implementation reads JKS files too, so whichever opens the file is fine. The
        // format reported to the user is the one the file really is, from detectFormat.
        KEYSTORE_FORMATS.firstNotNullOfOrNull { provider ->
            (tryRead(file, provider, password, declaredFormat) as? Availability.Available)
        } ?: unavailable(
            "${file.name} could not be opened. It is either not a keystore, or the password is " +
                "not the one it was created with.",
        )
    }

    private fun tryRead(
        file: Path,
        provider: String,
        password: Secret,
        declaredFormat: String,
    ): Availability<Keystore> {
        val keyStore = runCatching { KeyStore.getInstance(provider) }
            .getOrElse { return unavailable("This JDK cannot read $provider keystores.") }
        val loaded = runCatching {
            file.inputStream().use { stream -> keyStore.load(stream, password.reveal().toCharArray()) }
        }
        if (loaded.isFailure) {
            return unavailable("${file.name} is not a readable $provider keystore.")
        }
        val entries = keyStore.aliases().toList().mapNotNull { alias ->
            (keyStore.getCertificate(alias) as? X509Certificate)?.let { certificate ->
                entryOf(alias, certificate)
            }
        }
        return if (entries.isEmpty()) {
            unavailable("${file.name} holds no certificate Servante can read.")
        } else {
            Keystore(path = file.toString(), format = declaredFormat, entries = entries).available()
        }
    }

    private fun entryOf(alias: String, certificate: X509Certificate): KeystoreEntry = KeystoreEntry(
        alias = alias,
        keyAlgorithm = certificate.publicKey.algorithm,
        keySizeBits = keySizeOf(certificate),
        signatureAlgorithm = certificate.sigAlgName,
        // RFC 1779 rather than the default RFC 2253 form: it puts the common name first and separates
        // with a comma and a space, which is how keytool and the consoles show a distinguished name, so
        // the user sees the same string in Servante as everywhere else.
        subject = certificate.subjectX500Principal.getName(X500Principal.RFC1779),
        issuer = certificate.issuerX500Principal.getName(X500Principal.RFC1779),
        validFrom = Instant.fromEpochMilliseconds(certificate.notBefore.time),
        validUntil = Instant.fromEpochMilliseconds(certificate.notAfter.time),
        sha1 = fingerprint(certificate, "SHA-1"),
        sha256 = fingerprint(certificate, "SHA-256"),
    )

    /** Zero when the key type is one whose size this version does not know how to read. */
    private fun keySizeOf(certificate: X509Certificate): Int = when (val key = certificate.publicKey) {
        is RSAKey -> key.modulus.bitLength()
        is ECKey -> key.params.curve.field.fieldSize
        else -> 0
    }

    private fun fingerprint(certificate: X509Certificate, algorithm: String): Fingerprint =
        Fingerprint(MessageDigest.getInstance(algorithm).digest(certificate.encoded))

    /**
     * What the file actually is, from its first bytes, so the UI reports the truth rather than whichever
     * JDK provider happened to open it.
     *
     * JKS and JCEKS start with a magic number; PKCS12 is DER, which starts with a SEQUENCE tag. Anything
     * else is reported as unknown rather than guessed.
     */
    private fun detectFormat(file: Path): String {
        val header = runCatching {
            file.inputStream().use { stream -> ByteArray(MAGIC_BYTES).also { stream.read(it) } }
        }.getOrNull() ?: return UNKNOWN_FORMAT
        val magic = header.take(MAGIC_BYTES).joinToString("") { "%02x".format(it) }
        return when {
            magic.startsWith("feedfeed") -> "JKS"
            magic.startsWith("cececece") -> "JCEKS"
            header.firstOrNull() == DER_SEQUENCE_TAG -> "PKCS12"
            else -> UNKNOWN_FORMAT
        }
    }

    private companion object {
        private val KEYSTORE_FORMATS = listOf("PKCS12", "JKS")
        private const val MAGIC_BYTES = 4
        private const val DER_SEQUENCE_TAG: Byte = 0x30
        private const val UNKNOWN_FORMAT = "unknown"
    }
}
