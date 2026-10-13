package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.unavailable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** An APK signature scheme, in the order they were introduced. */
public enum class SignatureScheme(public val label: String) {
    V1("v1 (JAR signing)"),
    V2("v2 (APK Signature Scheme v2)"),
    V3("v3 (key rotation)"),
    V31("v3.1 (rotation targeting newer Android)"),
    V4("v4 (incremental install)"),
}

/** Who signed a build, and how. */
public data class BuildSignature(
    public val path: String,
    public val schemes: Set<SignatureScheme>,
    public val signerCertificates: List<SignerCertificate>,
) {
    public val isSigned: Boolean get() = schemes.isNotEmpty()
}

public data class SignerCertificate(
    public val subject: String,
    public val sha1: Fingerprint,
    public val sha256: Fingerprint,
    public val scheme: SignatureScheme,
)

/**
 * Reads which signature schemes a built APK or AAB carries and whose certificate each one holds.
 *
 * ## What this does and does not do
 *
 * It **reads** the signatures. It does not **verify** them cryptographically: it does not recompute the
 * digests of the archive's contents and check them against the signature. That distinction matters and
 * the UI states it. For the question M1 answers, "is this build signed with the key I think it is", the
 * certificate is the answer and reading it is enough. For "has this file been tampered with", it is not,
 * and the answer there is `apksig`, which is a Google Maven dependency; see
 * `docs/notes/build-verification.md`.
 *
 * ## Why it is written by hand
 *
 * The APK Signing Block is a documented, stable format: a magic string at the end of the archive before
 * the central directory, then length-prefixed ID to value pairs, one per scheme. Parsing it needs no
 * dependency, works for v1 through v4, and cannot fall behind the way a pinned signing library can.
 */
public class ApkSignatureReader(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    public suspend fun read(path: String): Availability<BuildSignature> = withContext(dispatcher) {
        val file = Path.of(path)
        if (!file.isRegularFile()) return@withContext unavailable("${file.name} is not there.")

        val schemes = linkedSetOf<SignatureScheme>()
        val certificates = mutableListOf<SignerCertificate>()

        val jarSigners = runCatching { readJarSignatures(file) }
            .getOrElse { return@withContext unavailable("${file.name} is not a readable archive.") }
        if (jarSigners.isNotEmpty()) {
            schemes += SignatureScheme.V1
            certificates += jarSigners
        }

        runCatching { readSigningBlock(file) }.getOrNull()?.let { block ->
            block.forEach { (scheme, signerCertificates) ->
                schemes += scheme
                certificates += signerCertificates
            }
        }

        if (Path.of("$path.idsig").exists()) schemes += SignatureScheme.V4

        BuildSignature(
            path = path,
            schemes = schemes,
            signerCertificates = certificates.distinctBy { it.sha256 to it.scheme },
        ).available()
    }

    /** v1 is JAR signing: a `META-INF` entry ending in `.RSA`, `.DSA` or `.EC` holding a PKCS#7 signature block. */
    private fun readJarSignatures(file: Path): List<SignerCertificate> = ZipFile(file.toFile()).use { zip ->
        zip.entries().asSequence()
            .filter { entry ->
                entry.name.startsWith("META-INF/") &&
                    SIGNATURE_BLOCK_EXTENSIONS.any { entry.name.endsWith(it, ignoreCase = true) }
            }
            .flatMap { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                certificatesIn(bytes).map { it.asSigner(SignatureScheme.V1) }
            }
            .toList()
    }

    /**
     * The APK Signing Block sits between the archive's contents and its central directory, ending with
     * the magic `APK Sig Block 42`. Inside it are length-prefixed ID to value pairs, one per scheme.
     */
    private fun readSigningBlock(file: Path): Map<SignatureScheme, List<SignerCertificate>> =
        RandomAccessFile(file.toFile(), "r").use { archive ->
            val centralDirectoryOffset = findCentralDirectoryOffset(archive) ?: return emptyMap()
            if (centralDirectoryOffset < SIGNING_BLOCK_FOOTER) return emptyMap()

            val footer = ByteArray(SIGNING_BLOCK_FOOTER)
            archive.seek(centralDirectoryOffset - SIGNING_BLOCK_FOOTER)
            archive.readFully(footer)
            val footerBuffer = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN)
            val blockSizeFromFooter = footerBuffer.getLong(0)
            val magic = String(footer, Long.SIZE_BYTES, MAGIC.length, Charsets.UTF_8)
            if (magic != MAGIC) return emptyMap()

            val blockStart = centralDirectoryOffset - blockSizeFromFooter - Long.SIZE_BYTES
            if (blockStart < 0) return emptyMap()
            val blockBytes = ByteArray((centralDirectoryOffset - blockStart).toInt())
            archive.seek(blockStart)
            archive.readFully(blockBytes)

            parsePairs(ByteBuffer.wrap(blockBytes).order(ByteOrder.LITTLE_ENDIAN))
        }

    private fun parsePairs(block: ByteBuffer): Map<SignatureScheme, List<SignerCertificate>> {
        val found = linkedMapOf<SignatureScheme, List<SignerCertificate>>()
        // Skip the leading size field; pairs run until the trailing size and magic.
        block.position(Long.SIZE_BYTES)
        val limit = block.capacity() - SIGNING_BLOCK_FOOTER
        while (block.position() + Long.SIZE_BYTES + Int.SIZE_BYTES <= limit) {
            val pairSize = block.long
            if (pairSize < Int.SIZE_BYTES || block.position() + pairSize > limit) break
            val id = block.int
            val value = ByteArray((pairSize - Int.SIZE_BYTES).toInt())
            block.get(value)
            SCHEME_BLOCK_IDS[id]?.let { scheme ->
                found[scheme] = certificatesIn(value).map { it.asSigner(scheme) }
            }
        }
        return found
    }

    /**
     * Every DER certificate in a blob, found by asking the JDK to parse each candidate.
     *
     * The signature blocks nest certificates inside length-prefixed structures that differ per scheme
     * version. Rather than track each layout, which is what breaks when a new scheme lands, this scans
     * for the DER tag of an X.509 certificate and lets the certificate factory decide what is real.
     */
    private fun certificatesIn(bytes: ByteArray): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        val found = linkedMapOf<String, X509Certificate>()
        for (offset in 0 until (bytes.size - MINIMUM_CERTIFICATE_BYTES).coerceAtLeast(0)) {
            val certificate = certificateAt(bytes, offset, factory) ?: continue
            found[certificate.serialNumber.toString() + certificate.subjectX500Principal.name] =
                certificate
        }
        return found.values.toList()
    }

    /** A DER certificate starting exactly at [offset], or null if there is not one there. */
    private fun certificateAt(bytes: ByteArray, offset: Int, factory: CertificateFactory): X509Certificate? {
        if (bytes[offset] != DER_SEQUENCE_TAG || bytes[offset + 1] != DER_LONG_FORM_TWO_BYTES) return null
        val length = bytes.beBigEndianShort(offset + DER_LENGTH_OFFSET)
        val end = offset + DER_HEADER_BYTES + length
        if (end > bytes.size) return null
        return runCatching {
            val candidate = bytes.copyOfRange(offset, end)
            factory.generateCertificate(ByteArrayInputStream(candidate)) as X509Certificate
        }.getOrNull()
    }

    /** The two bytes at [at] as an unsigned big-endian value, which is how DER writes a length. */
    private fun ByteArray.beBigEndianShort(at: Int): Int =
        ((this[at].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (this[at + 1].toInt() and BYTE_MASK)

    private fun X509Certificate.asSigner(scheme: SignatureScheme) = SignerCertificate(
        subject = subjectX500Principal.getName(javax.security.auth.x500.X500Principal.RFC1779),
        sha1 = Fingerprint(java.security.MessageDigest.getInstance("SHA-1").digest(encoded)),
        sha256 = Fingerprint(java.security.MessageDigest.getInstance("SHA-256").digest(encoded)),
        scheme = scheme,
    )

    /** The offset of the central directory, from the end of central directory record. */
    private fun findCentralDirectoryOffset(archive: RandomAccessFile): Long? {
        val length = archive.length()
        val searchFrom = (length - MAX_END_OF_CENTRAL_DIRECTORY).coerceAtLeast(0)
        val tail = ByteArray((length - searchFrom).toInt())
        archive.seek(searchFrom)
        archive.readFully(tail)
        val buffer = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
        for (offset in tail.size - END_OF_CENTRAL_DIRECTORY_MINIMUM downTo 0) {
            if (buffer.getInt(offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                return buffer.getInt(offset + CENTRAL_DIRECTORY_OFFSET_FIELD).toLong() and
                    UNSIGNED_INT_MASK
            }
        }
        return null
    }

    private companion object {
        private const val MAGIC = "APK Sig Block 42"
        private const val SIGNING_BLOCK_FOOTER = 24 // 8 bytes of size plus the 16-byte magic.
        private val SCHEME_BLOCK_IDS = mapOf(
            0x7109871a to SignatureScheme.V2,
            -0x0fac9740 to SignatureScheme.V3, // 0xf05368c0
            0x1b93ad61 to SignatureScheme.V31,
        )
        private val SIGNATURE_BLOCK_EXTENSIONS = listOf(".RSA", ".DSA", ".EC")

        private const val DER_SEQUENCE_TAG: Byte = 0x30
        private const val DER_LONG_FORM_TWO_BYTES: Byte = -0x7e // 0x82
        private const val DER_HEADER_BYTES = 4
        private const val DER_LENGTH_OFFSET = 2
        private const val BYTE_MASK = 0xFF
        private const val MINIMUM_CERTIFICATE_BYTES = 64

        private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
        private const val END_OF_CENTRAL_DIRECTORY_MINIMUM = 22
        private const val CENTRAL_DIRECTORY_OFFSET_FIELD = 16
        private const val MAX_END_OF_CENTRAL_DIRECTORY = 66_000L
        private const val UNSIGNED_INT_MASK = 0xFFFFFFFFL
    }
}
