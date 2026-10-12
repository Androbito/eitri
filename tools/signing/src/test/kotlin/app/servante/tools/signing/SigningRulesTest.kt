package app.servante.tools.signing

import app.servante.core.Severity
import app.servante.core.SourceLocation
import app.servante.core.available
import app.servante.core.fakes.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Behaviours:
 * - a missing Play signing registration is critical; a missing debug one is a warning
 * - a literal password in a build file is a warning, and the finding never shows the password
 * - a password from the environment is not reported at all
 * - an expired certificate is critical, one expiring within a year is a warning, otherwise healthy
 * - a short RSA key and a SHA-1 signed certificate are warnings with their CWE mapping
 * - a release build type signed with the debug config is critical
 * - a module with no signing config at all is information, not a warning
 */
class SigningRulesTest {
    private val clock = FixedClock(Instant.parse("2026-10-12T20:30:00Z"))

    private fun fingerprintOf(hex: String) = Fingerprint(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    private fun identity(
        kind: IdentityKind,
        validUntil: Instant? = null,
        keyAlgorithm: String? = "RSA",
        keySizeBits: Int? = 4096,
    ) = SigningIdentity(
        kind = kind,
        label = when (kind) {
            IdentityKind.Debug -> "Debug keystore"
            IdentityKind.Upload -> "Upload key"
            IdentityKind.PlayAppSigning -> "Play app signing key"
            IdentityKind.Other -> "Other key"
        },
        source = "release.jks, alias upload",
        sha1 = fingerprintOf("8914b81777ba9a35c1ace9bed6ee805e2cd35731"),
        sha256 = null,
        keyAlgorithm = keyAlgorithm,
        keySizeBits = keySizeBits,
        validUntil = validUntil,
    )

    private fun matrixMissingFor(kind: IdentityKind) = FingerprintComparison.build(
        packageName = "app.fjord.notes",
        identities = listOf(identity(kind)),
        firebase = GoogleServicesConfig("app/google-services.json", "fjord-notes", emptyList())
            .available(),
        playCertificate = CertificateFingerprints(sha1 = fingerprintOf("aabb")).available(),
    )

    @Test
    fun `a missing Play signing registration is critical`() {
        val findings = SigningRules.missingRegistrations(matrixMissingFor(IdentityKind.PlayAppSigning))

        assertTrue(findings.isNotEmpty())
        assertTrue(findings.all { it.severity == Severity.Critical })
        assertTrue(findings.any { "Firebase" in it.title })
    }

    @Test
    fun `a missing registration for another key is a warning`() {
        val findings = SigningRules.missingRegistrations(matrixMissingFor(IdentityKind.Upload))

        assertTrue(findings.isNotEmpty())
        assertTrue(findings.all { it.severity == Severity.Warning })
    }

    @Test
    fun `a literal password is a warning and the finding does not show it`() {
        val declarations = SigningDeclarations(
            configs = listOf(
                SigningConfigDeclaration(
                    name = "release",
                    storeFile = ValueSource.Literal("../keystores/release.jks"),
                    storePassword = ValueSource.Literal("fjord-release-2024"),
                    keyAlias = ValueSource.Literal("upload"),
                    keyPassword = ValueSource.Literal("fjord-release-2024"),
                    location = SourceLocation("app/build.gradle.kts", line = 42),
                ),
            ),
            buildTypeConfigs = emptyMap(),
        )

        val findings = SigningRules.committedSecrets(declarations, "app/build.gradle.kts")

        assertEquals(2, findings.size)
        assertTrue(findings.all { it.severity == Severity.Warning })
        assertTrue(findings.all { finding -> finding.evidence.none { "fjord-release-2024" in it.value } })
        assertEquals("fjor…2024", findings.first().evidence.single().value)
        assertEquals(42, findings.first().location?.line)
        assertTrue(findings.all { finding -> finding.standards.any { it.id == "CWE-798" } })
    }

    @Test
    fun `a password from the environment is not reported`() {
        val declarations = SigningDeclarations(
            configs = listOf(
                SigningConfigDeclaration(
                    name = "release",
                    storeFile = ValueSource.Literal("keystore.jks"),
                    storePassword = ValueSource.Environment("KEYSTORE_PASSWORD"),
                    keyAlias = ValueSource.Literal("release"),
                    keyPassword = ValueSource.GradleProperty("keyPassword"),
                    location = SourceLocation("mobile/build.gradle"),
                ),
            ),
            buildTypeConfigs = emptyMap(),
        )

        assertTrue(SigningRules.committedSecrets(declarations, "mobile/build.gradle").isEmpty())
    }

    @Test
    fun `an expired certificate is critical`() {
        val finding = SigningRules.certificateValidity(
            identity(IdentityKind.Upload, validUntil = Instant.parse("2025-01-01T00:00:00Z")),
            clock,
        )

        assertEquals(Severity.Critical, finding?.severity)
        assertTrue("expired" in finding?.title.orEmpty())
    }

    @Test
    fun `a certificate expiring within a year is a warning`() {
        val finding = SigningRules.certificateValidity(
            identity(IdentityKind.Upload, validUntil = Instant.parse("2027-01-01T00:00:00Z")),
            clock,
        )

        assertEquals(Severity.Warning, finding?.severity)
        assertTrue("expires in 80 days" in finding?.title.orEmpty(), "was ${finding?.title}")
    }

    @Test
    fun `a certificate with years left is healthy`() {
        val finding = SigningRules.certificateValidity(
            identity(IdentityKind.Upload, validUntil = Instant.parse("2056-10-04T00:00:00Z")),
            clock,
        )

        assertEquals(Severity.Healthy, finding?.severity)
        assertTrue("2056" in finding?.title.orEmpty())
    }

    @Test
    fun `a certificate with no expiry known produces no finding`() {
        assertNull(SigningRules.certificateValidity(identity(IdentityKind.Upload), clock))
    }

    @Test
    fun `a short RSA key and a SHA-1 signature are warnings`() {
        val findings = SigningRules.weakAlgorithms(
            identity(IdentityKind.Upload, keySizeBits = 1024),
            signatureAlgorithm = "SHA1withRSA",
        )

        assertEquals(2, findings.size)
        assertTrue(findings.all { it.severity == Severity.Warning })
        assertTrue(findings.any { finding -> finding.standards.any { it.id == "CWE-326" } })
        assertTrue(findings.any { finding -> finding.standards.any { it.id == "CWE-327" } })
    }

    @Test
    fun `a 4096-bit RSA key signed with SHA-384 raises nothing`() {
        val findings = SigningRules.weakAlgorithms(
            identity(IdentityKind.Upload, keySizeBits = 4096),
            signatureAlgorithm = "SHA384withRSA",
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `a release build type signed with the debug config is critical`() {
        val findings = SigningRules.releaseSignedWithDebugKey(
            SigningDeclarations(
                configs = emptyList(),
                buildTypeConfigs = mapOf("release" to "debug", "debug" to "debug"),
            ),
        )

        assertEquals(1, findings.size)
        assertEquals(Severity.Critical, findings.single().severity)
        assertTrue("release" in findings.single().title)
    }

    @Test
    fun `a module with no signing config at all is information`() {
        val finding = SigningRules.missingReleaseConfig(
            SigningDeclarations(emptyList(), emptyMap()),
            "core/build.gradle.kts",
        )

        assertEquals(Severity.Info, finding?.severity)
        assertFalse(finding?.explanation.orEmpty().isEmpty())
    }
}
