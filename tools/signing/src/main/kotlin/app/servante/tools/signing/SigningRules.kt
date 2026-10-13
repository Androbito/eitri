package app.servante.tools.signing

import app.servante.core.Evidence
import app.servante.core.Finding
import app.servante.core.FindingId
import app.servante.core.Fix
import app.servante.core.Severity
import app.servante.core.SourceLocation
import app.servante.core.StandardRef
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Turns what was read into findings.
 *
 * Pure functions over the already-read data: no file access, no clock of its own. That is what makes
 * every rule here testable in a line, and it is where the question M1 answers is actually decided.
 */
public object SigningRules {
    /** Below this, a certificate is reported as expiring. Play requires a key valid past 2033. */
    public val EXPIRY_WARNING_WINDOW: Duration = 365.days

    private const val WEAK_RSA_BITS = 2048
    private val WEAK_SIGNATURE_ALGORITHMS = setOf("MD5withRSA", "SHA1withRSA", "MD2withRSA")

    /**
     * The headline finding: a key the app is distributed with whose fingerprint is not registered where
     * it has to be. For the Play app signing certificate that means sign-in fails for every install
     * from Play while working perfectly in debug, which is the question the tool exists to answer.
     */
    public fun missingRegistrations(matrix: FingerprintMatrix): List<Finding> =
        matrix.missingPairs().map { (identity, consumer) ->
            val cell = matrix.cell(identity, consumer)
            Finding(
                id = FindingId("signing.not-registered.${identity.slug()}.${consumer.name.lowercase()}"),
                severity = if (identity.kind == IdentityKind.PlayAppSigning) {
                    Severity.Critical
                } else {
                    Severity.Warning
                },
                title = when (consumer) {
                    Consumer.Firebase ->
                        "Add the ${identity.label} SHA-1 and SHA-256 to Firebase"

                    Consumer.OAuthClient ->
                        "Register the ${identity.label} fingerprint on an Android OAuth client"

                    Consumer.Play -> "Play does not know the ${identity.label}"
                },
                explanation = cell.reason
                    ?: "This fingerprint is not registered for ${matrix.packageName}.",
                location = SourceLocation("google-services.json"),
                evidence = listOfNotNull(
                    identity.sha1?.let { Evidence("SHA-1", it.formatted) },
                    identity.sha256?.let { Evidence("SHA-256", it.formatted) },
                ),
            )
        }

    /**
     * A row of the matrix that matters and could not be compared.
     *
     * This exists because of what the Play signing spike found: `google-services.json` records SHA-1
     * fingerprints and the Play Developer API exposes SHA-256, so the single most important comparison
     * in this tool, the Play app signing certificate against what Firebase knows, cannot be made from
     * those two sources alone. Saying "unknown" in a cell and nothing else would leave the user thinking
     * the check passed. This finding says what is not known, why, and what to do about it.
     */
    public fun unverifiableRegistrations(matrix: FingerprintMatrix): List<Finding> {
        val playRow = matrix.identities.firstOrNull { it.kind == IdentityKind.PlayAppSigning }
            ?: return emptyList()
        val firebaseCell = matrix.cell(playRow, Consumer.Firebase)
        if (firebaseCell.state != MatchState.Unknown) return emptyList()
        return listOf(
            Finding(
                id = FindingId("signing.play-registration-unverified"),
                severity = Severity.Warning,
                title = "Servante cannot confirm the Play signing certificate is registered in Firebase",
                explanation = firebaseCell.reason
                    ?: "The Play app signing certificate could not be compared against Firebase.",
                location = SourceLocation("google-services.json"),
                evidence = listOfNotNull(
                    playRow.sha256?.let { Evidence("Play SHA-256", it.formatted) },
                ),
                fix = Fix.Guidance(
                    "Open the Play Console, App signing, and copy the app signing certificate's SHA-1 " +
                        "and SHA-256. Then open the Firebase console, Project settings, and check both " +
                        "are listed for ${matrix.packageName}. If either is missing, add it: Play " +
                        "re-signs your app with that key, so Google Sign-In, Maps and App Links check " +
                        "against it and not against your upload key.",
                ),
            ),
        )
    }

    /**
     * A keystore password written in a file inside the repository. Anyone with read access can sign a
     * build as the project, which is why this is a finding even though the build works.
     *
     * Only [ValueSource.Literal] counts. A password from the environment or a Gradle property outside
     * the repository is the correct arrangement and must not be reported, or the tool trains people to
     * ignore it.
     */
    public fun committedSecrets(declarations: SigningDeclarations, buildFilePath: String): List<Finding> =
        declarations.configs.flatMap { config ->
            listOfNotNull(
                config.storePassword.asLiteral()?.let { value ->
                    literalPasswordFinding(config, "storePassword", value, buildFilePath)
                },
                config.keyPassword.asLiteral()?.let { value ->
                    literalPasswordFinding(config, "keyPassword", value, buildFilePath)
                },
            )
        }

    private fun literalPasswordFinding(
        config: SigningConfigDeclaration,
        property: String,
        value: String,
        buildFilePath: String,
    ) = Finding(
        id = FindingId("signing.password-committed.${config.name}.$property"),
        severity = Severity.Warning,
        title = "Keystore password committed to the repository",
        explanation = "The $property of the ${config.name} signing config is written in $buildFilePath. " +
            "Anyone who can read the repository can sign a build as you. Read it from an environment " +
            "variable or from a properties file outside the repository instead.",
        location = config.location,
        // The value is deliberately not shown, not even masked. Unlike an API key, a keystore password
        // needs no identifying: the user knows it. Showing part of it would put part of a live secret
        // into a finding that is exported as Markdown and cached on disk.
        evidence = listOf(Evidence(property, "${value.length} characters, not shown")),
        standards = listOf(StandardRef("CWE-798", "Use of Hard-coded Credentials")),
    )

    /** Expiry: critical once a certificate has expired, a warning while it is close. */
    public fun certificateValidity(identity: SigningIdentity, clock: Clock): Finding? {
        val validUntil = identity.validUntil ?: return null
        val now = clock.now()
        val remaining = validUntil - now
        return when {
            remaining.isNegative() -> Finding(
                id = FindingId("signing.certificate-expired.${identity.kind.name.lowercase()}"),
                severity = Severity.Critical,
                title = "${identity.label} certificate has expired",
                explanation = "It expired on ${validUntil.dayString()}. Play rejects an upload signed " +
                    "with an expired certificate, and an app signed with one cannot be updated.",
                location = SourceLocation(identity.source),
            )

            remaining < EXPIRY_WARNING_WINDOW -> Finding(
                id = FindingId("signing.certificate-expiring.${identity.kind.name.lowercase()}"),
                severity = Severity.Warning,
                title = "${identity.label} certificate expires in ${remaining.inWholeDays} days",
                explanation = "It expires on ${validUntil.dayString()}. An upload key can be replaced " +
                    "through Play support, but not in an afternoon, so start before it expires.",
                location = SourceLocation(identity.source),
            )

            else -> Finding(
                id = FindingId("signing.certificate-valid.${identity.kind.name.lowercase()}"),
                severity = Severity.Healthy,
                title = "${identity.label} certificate valid until ${validUntil.yearString()}",
                explanation = "Nothing to do. ${identity.keyAlgorithm.orEmpty()} " +
                    "${identity.keySizeBits ?: ""}".trim().ifEmpty { "The key" } + " is in date.",
                location = SourceLocation(identity.source),
            )
        }
    }

    /** A key or signature algorithm too weak to be relied on. */
    public fun weakAlgorithms(identity: SigningIdentity, signatureAlgorithm: String?): List<Finding> {
        val findings = mutableListOf<Finding>()
        val size = identity.keySizeBits
        if (identity.keyAlgorithm == "RSA" && size != null && size in 1 until WEAK_RSA_BITS) {
            findings += Finding(
                id = FindingId("signing.weak-key.${identity.kind.name.lowercase()}"),
                severity = Severity.Warning,
                title = "${identity.label} uses a $size-bit RSA key",
                explanation = "Play requires at least $WEAK_RSA_BITS bits for an upload key. A key this " +
                    "short cannot be used for a new app and should be replaced for an existing one.",
                location = SourceLocation(identity.source),
                standards = listOf(StandardRef("CWE-326", "Inadequate Encryption Strength")),
            )
        }
        if (signatureAlgorithm != null && signatureAlgorithm in WEAK_SIGNATURE_ALGORITHMS) {
            findings += Finding(
                id = FindingId("signing.weak-signature.${identity.kind.name.lowercase()}"),
                severity = Severity.Warning,
                title = "${identity.label} certificate is signed with $signatureAlgorithm",
                explanation = "SHA-1 and MD5 are no longer considered safe for signatures. The key " +
                    "itself is unaffected, but a certificate signed this way should be regenerated.",
                location = SourceLocation(identity.source),
                standards = listOf(StandardRef("CWE-327", "Use of a Broken Cryptographic Algorithm")),
            )
        }
        return findings
    }

    /** A release build type signed with the debug key ships a build nothing else will trust. */
    public fun releaseSignedWithDebugKey(declarations: SigningDeclarations): List<Finding> =
        declarations.buildTypeConfigs
            .filter { (buildType, configName) ->
                !buildType.equals("debug", ignoreCase = true) &&
                    configName.equals("debug", ignoreCase = true)
            }
            .map { (buildType, _) ->
                Finding(
                    id = FindingId("signing.release-uses-debug-key.$buildType"),
                    severity = Severity.Critical,
                    title = "The $buildType build type is signed with the debug key",
                    explanation = "Play rejects a build signed with the debug certificate, and Firebase " +
                        "and Maps keys registered for your release certificate will not work in it.",
                    location = declarations.configs.firstOrNull()?.location
                        ?: SourceLocation("build.gradle.kts"),
                )
            }

    /** No signing config at all for a non-debug build type. */
    public fun missingReleaseConfig(declarations: SigningDeclarations, buildFilePath: String): Finding? {
        if (declarations.configs.isEmpty() && declarations.buildTypeConfigs.isEmpty()) {
            return Finding(
                id = FindingId("signing.no-signing-config"),
                severity = Severity.Info,
                title = "This module declares no signing config",
                explanation = "Release builds will be unsigned unless the key is passed on the command " +
                    "line or configured elsewhere. That is a valid arrangement; Servante mentions it " +
                    "because it cannot then tell you which key your releases use.",
                location = SourceLocation(buildFilePath),
            )
        }
        return null
    }

    private fun ValueSource.asLiteral(): String? = (this as? ValueSource.Literal)?.value
}

private const val YEAR_LENGTH = 4

private fun Instant.dayString(): String = toString().substringBefore('T')

private fun Instant.yearString(): String = toString().take(YEAR_LENGTH)
