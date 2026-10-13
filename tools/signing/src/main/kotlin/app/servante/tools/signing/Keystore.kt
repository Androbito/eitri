package app.servante.tools.signing

import kotlin.time.Instant

/**
 * A certificate fingerprint, in the forms the consoles ask for.
 *
 * Google's consoles want different shapes of the same bytes: the Play Console and `keytool` show
 * colon-separated uppercase hex, the Firebase console accepts that too, and `google-services.json`
 * stores plain lowercase hex. Keeping all three on one type is what makes the copy helpers in the
 * designs possible without the UI reformatting strings.
 */
public data class Fingerprint(private val bytes: ByteArray) {
    public val formatted: String = bytes.joinToString(":") { byte -> "%02X".format(byte) }

    public val plainLowercase: String = bytes.joinToString("") { byte -> "%02x".format(byte) }

    /** The first and last four characters, as the fingerprint matrix shows them: `3A:91:0C:E4 … 7B:22`. */
    public fun abbreviated(): String {
        val groups = formatted.split(':')
        if (groups.size <= ABBREVIATION_GROUPS * 2) return formatted
        return groups.take(ABBREVIATION_GROUPS).joinToString(":") +
            " … " +
            groups.takeLast(2).joinToString(":")
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Fingerprint && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = formatted

    private companion object {
        private const val ABBREVIATION_GROUPS = 4
    }
}

/** One key and certificate in a keystore. */
public data class KeystoreEntry(
    public val alias: String,
    public val keyAlgorithm: String,
    public val keySizeBits: Int,
    public val signatureAlgorithm: String,
    public val subject: String,
    public val issuer: String,
    public val validFrom: Instant,
    public val validUntil: Instant,
    public val sha1: Fingerprint,
    public val sha256: Fingerprint,
) {
    public val isSelfSigned: Boolean get() = subject == issuer
}

/**
 * A keystore Servante read.
 *
 * @param path the file, so a finding can point at it.
 * @param format what it turned out to be, `JKS` or `PKCS12`. The caller does not say which: a `.jks`
 *   extension is a convention, not a guarantee, and a keystore created by a recent `keytool` is PKCS12
 *   whatever it is called.
 */
public data class Keystore(public val path: String, public val format: String, public val entries: List<KeystoreEntry>)
