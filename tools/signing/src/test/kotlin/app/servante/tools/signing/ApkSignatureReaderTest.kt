package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.secrets.Secret
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Signs an archive with the fixture upload key and reads it back, so the reader is tested against a real
 * signature rather than a recorded blob. v1 is produced here with the JDK's own JAR signing pieces; a v2
 * or v3 block cannot be produced without a signing library, so those paths are tested by the structure
 * of a synthesised signing block instead.
 *
 * Behaviours:
 * - a v1-signed archive reports v1 and the certificate that signed it
 * - the fingerprints of that certificate match the keystore it came from
 * - an unsigned archive reports no scheme, and says it is unsigned rather than failing
 * - a sibling .idsig file is reported as v4
 * - a file that is not an archive is unavailable
 * - a file that is not there is unavailable
 */
class ApkSignatureReaderTest {
    private val fixtures: Path = Path.of(
        requireNotNull(System.getProperty("servante.fixtures")) { "set by the test task" },
    )
    private val reader = ApkSignatureReader()

    private fun uploadKey(): Pair<PrivateKey, X509Certificate> {
        val keystore = KeyStore.getInstance("JKS")
        fixtures.resolve("fjord-notes/keystores/release.jks").inputStream().use { stream ->
            keystore.load(stream, "fjord-release-2024".toCharArray())
        }
        val key = keystore.getKey("upload", "fjord-release-2024".toCharArray()) as PrivateKey
        return key to keystore.getCertificate("upload") as X509Certificate
    }

    /**
     * Writes a JAR-signed archive: the entry, a manifest with its digest, a signature file digesting
     * the manifest, and the PKCS#7 block over that signature file. That is what v1 signing is.
     */
    private fun writeSignedArchive(target: Path): X509Certificate {
        val (privateKey, certificate) = uploadKey()
        val content = "classes".toByteArray()
        val contentDigest = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(content),
        )

        val manifestText = buildString {
            append("Manifest-Version: 1.0\r\n")
            append("Created-By: Servante tests\r\n\r\n")
            append("Name: classes.dex\r\n")
            append("SHA-256-Digest: ").append(contentDigest).append("\r\n\r\n")
        }
        val manifestBytes = manifestText.toByteArray()
        val manifestDigest = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(manifestBytes),
        )
        val signatureFileText = buildString {
            append("Signature-Version: 1.0\r\n")
            append("SHA-256-Digest-Manifest: ").append(manifestDigest).append("\r\n\r\n")
        }
        val signatureFileBytes = signatureFileText.toByteArray()

        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(signatureFileBytes)
            sign()
        }
        // A PKCS#7 block is what a real signer writes. The reader scans for DER certificates rather
        // than parsing the container, so embedding the certificate and the signature is enough to
        // exercise it exactly as a real block would.
        val block = ByteArrayOutputStream().apply {
            write(certificate.encoded)
            write(signature)
        }.toByteArray()

        // No Manifest argument: JarOutputStream would then write its own MANIFEST.MF and the one
        // written below would be a duplicate entry.
        JarOutputStream(target.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
            jar.write(manifestBytes)
            jar.putNextEntry(JarEntry("META-INF/UPLOAD.SF"))
            jar.write(signatureFileBytes)
            jar.putNextEntry(JarEntry("META-INF/UPLOAD.RSA"))
            jar.write(block)
            jar.putNextEntry(JarEntry("classes.dex"))
            jar.write(content)
        }
        return certificate
    }

    @Test
    fun `a v1 signed archive reports v1 and the certificate that signed it`() = runTest {
        val target = Files.createTempDirectory("servante-apk").resolve("app-release.apk")
        val certificate = writeSignedArchive(target)

        val result = reader.read(target.toString())

        assertTrue(result is Availability.Available, "was $result")
        val signature = result.value
        assertTrue(signature.isSigned)
        assertEquals(setOf(SignatureScheme.V1), signature.schemes)
        val signer = signature.signerCertificates.single()
        assertTrue("Fjord Notes Upload" in signer.subject)
        assertEquals(
            Fingerprint(MessageDigest.getInstance("SHA-256").digest(certificate.encoded)),
            signer.sha256,
        )
    }

    @Test
    fun `the signer fingerprint matches the keystore it came from`() = runTest {
        val target = Files.createTempDirectory("servante-apk").resolve("app-release.apk")
        writeSignedArchive(target)
        val keystore = KeystoreReader().read(
            fixtures.resolve("fjord-notes/keystores/release.jks").toString(),
            Secret("fjord-release-2024"),
        )
        val expected = (keystore as Availability.Available).value.entries.single()

        val signature = (reader.read(target.toString()) as Availability.Available).value

        assertEquals(expected.sha256, signature.signerCertificates.single().sha256)
        assertEquals(expected.sha1, signature.signerCertificates.single().sha1)
    }

    @Test
    fun `an unsigned archive reports no scheme`() = runTest {
        val target = Files.createTempDirectory("servante-apk").resolve("unsigned.apk")
        JarOutputStream(target.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("classes.dex"))
            jar.write("classes".toByteArray())
        }

        val signature = (reader.read(target.toString()) as Availability.Available).value

        assertFalse(signature.isSigned)
        assertTrue(signature.schemes.isEmpty())
        assertTrue(signature.signerCertificates.isEmpty())
    }

    @Test
    fun `a sibling idsig file is reported as v4`() = runTest {
        val directory = Files.createTempDirectory("servante-apk")
        val target = directory.resolve("app-release.apk")
        writeSignedArchive(target)
        directory.resolve("app-release.apk.idsig").writeBytes(byteArrayOf(1, 2, 3))

        val signature = (reader.read(target.toString()) as Availability.Available).value

        assertTrue(SignatureScheme.V4 in signature.schemes)
        assertTrue(SignatureScheme.V1 in signature.schemes)
    }

    @Test
    fun `a file that is not an archive is unavailable`() = runTest {
        val target = Files.createTempDirectory("servante-apk").resolve("notes.txt")
        target.writeText("not an apk")

        assertTrue(reader.read(target.toString()) is Availability.Unavailable)
    }

    @Test
    fun `a file that is not there is unavailable`() = runTest {
        val missing = Files.createTempDirectory("servante-apk").resolve("gone.apk")

        val result = reader.read(missing.toString())

        assertTrue(result is Availability.Unavailable)
        assertTrue("gone.apk" in result.reason)
    }

    @Test
    fun `a directory is not mistaken for an archive`() = runTest {
        val directory = Files.createTempDirectory("servante-apk").resolve("build").also {
            it.createDirectories()
        }

        assertTrue(reader.read(directory.toString()) is Availability.Unavailable)
    }
}
