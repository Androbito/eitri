package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.Finding
import app.servante.core.Progress
import app.servante.core.Severity
import app.servante.core.ToolId
import app.servante.core.ToolResult
import app.servante.core.ToolState
import app.servante.core.project.AndroidProject
import app.servante.core.project.GradleModule
import app.servante.core.secrets.Secret
import app.servante.core.unavailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/** Everything the Signing screen shows, in one value. */
public data class SigningReport(
    public val result: ToolResult,
    public val matrix: FingerprintMatrix,
    public val identities: List<SigningIdentity>,
    public val keystores: List<KeystoreCandidate>,
)

/**
 * Asked for a keystore's password when one is needed.
 *
 * Returning null means the user skipped it, which is a supported answer: the key is then reported as
 * unreadable with that reason rather than the whole check failing. The password is used once by the
 * reader and dropped; nothing here stores it.
 */
public fun interface KeystorePasswordProvider {
    public suspend fun passwordFor(keystorePath: String): Secret?
}

/**
 * The Play app signing certificate, when a Play account is connected.
 *
 * It returns whichever fingerprints could be obtained rather than one: the Play Developer API exposes
 * SHA-256 only, and recovering the SHA-1 means downloading a generated APK, which is a separate,
 * explicit action. See `docs/notes/play-signing-cert.md`.
 */
public fun interface PlayAppSigningCertificates {
    public suspend fun forPackage(packageName: String): Availability<CertificateFingerprints>
}

/**
 * The Signing tool: why does sign-in work in debug and fail in release?
 *
 * It reads, compares and reports. Nothing here writes, and nothing here runs Gradle. The steps are
 * emitted as progress so the loading state in the designs can say what is happening, and the result is
 * one of the six tool states rather than a value plus a pile of nullable fields.
 */
public class SigningTool(
    private val fileSystem: SigningFileSystem,
    private val keystoreReader: KeystoreReader,
    private val googleServicesReader: GoogleServicesReader,
    private val signingConfigReader: SigningConfigReader,
    private val passwordProvider: KeystorePasswordProvider,
    private val playCertificates: PlayAppSigningCertificates,
    private val clock: Clock,
) {
    public fun check(
        project: AndroidProject,
        userAddedKeystores: List<String> = emptyList(),
    ): Flow<ToolState<SigningReport>> = flow {
        val applicationModule = project.applicationModules.firstOrNull()
            ?: run {
                emit(
                    ToolState.Unavailable(
                        "No module in ${project.name} applies the Android application plugin, so there " +
                            "is no app to check the signing of.",
                    ),
                )
                return@flow
            }

        emit(ToolState.Loading(Progress("Reading build files", step = 1, totalSteps = TOTAL_STEPS)))
        val declarationsByModule = readDeclarations(project)
        val declarations = declarationsByModule[applicationModule.path]
            ?: SigningDeclarations(emptyList(), emptyMap())

        emit(ToolState.Loading(Progress("Finding keystores", step = 2, totalSteps = TOTAL_STEPS)))
        val keystores = KeystoreDiscovery(fileSystem)
            .discover(project, declarationsByModule, userAddedKeystores)

        emit(
            ToolState.Loading(
                Progress("Reading ${keystores.count { it.exists }} keystores", READ_KEYSTORES_STEP, TOTAL_STEPS),
            ),
        )
        val identities = readIdentities(keystores, declarations)

        emit(ToolState.Loading(Progress("Comparing fingerprints", step = 4, totalSteps = TOTAL_STEPS)))
        val packageName = applicationModule.applicationId(project, fileSystem) ?: project.name
        val firebase = readGoogleServices(project, applicationModule)
        val playCertificate = playCertificates.forPackage(packageName)
        val withPlay = identities + playIdentity(playCertificate)
        val matrix = FingerprintComparison.build(packageName, withPlay, firebase, playCertificate)

        val findings = collectFindings(matrix, declarations, withPlay, applicationModule)
        val result = ToolResult(
            tool = ToolId.Signing,
            findings = findings,
            checksPassed = checksRun(withPlay, matrix) - findings.count { it.severity != Severity.Healthy },
        )
        val report = SigningReport(result, matrix, withPlay, keystores)

        emit(
            if (result.worstSeverity == Severity.Healthy) {
                ToolState.Healthy(report)
            } else {
                ToolState.Findings(report)
            },
        )
    }

    private fun readDeclarations(project: AndroidProject): Map<String, SigningDeclarations> =
        project.modules.mapNotNull { module ->
            val directory = module.directory ?: return@mapNotNull null
            val buildFile = BUILD_FILE_NAMES
                .map { fileSystem.resolve(fileSystem.resolve(project.rootPath, directory), it) }
                .firstOrNull { fileSystem.exists(it) }
                ?: return@mapNotNull null
            val relative = "$directory/${fileSystem.fileName(buildFile)}"
            when (val text = fileSystem.readText(buildFile)) {
                is Availability.Unavailable -> null

                is Availability.Available ->
                    module.path to signingConfigReader.read(relative, text.value)
            }
        }.toMap()

    private suspend fun readIdentities(
        keystores: List<KeystoreCandidate>,
        declarations: SigningDeclarations,
    ): List<SigningIdentity> = keystores.filter { it.exists }.flatMap { candidate ->
        val password = passwordProvider.passwordFor(candidate.path)
            ?: return@flatMap listOf(unreadableIdentity(candidate, "No password was given for it."))
        when (val keystore = keystoreReader.read(candidate.path, password)) {
            is Availability.Unavailable -> listOf(unreadableIdentity(candidate, keystore.reason))

            is Availability.Available -> keystore.value.entries.map { entry ->
                entry.asIdentity(candidate, declarations)
            }
        }
    }

    private fun unreadableIdentity(candidate: KeystoreCandidate, reason: String) = SigningIdentity(
        kind = candidate.kind(declaredAlias = null),
        label = candidate.label(),
        source = fileSystem.fileName(candidate.path),
        sha1 = null,
        sha256 = null,
        unreadableReason = reason,
    )

    private fun KeystoreEntry.asIdentity(
        candidate: KeystoreCandidate,
        declarations: SigningDeclarations,
    ): SigningIdentity {
        val declaredAlias = declarations.configs
            .firstOrNull { it.name == candidate.configName }
            ?.keyAlias
            ?.let { (it as? ValueSource.Literal)?.value }
        return SigningIdentity(
            kind = candidate.kind(declaredAlias = declaredAlias ?: alias),
            label = candidate.label(),
            source = "${fileSystem.fileName(candidate.path)}, alias $alias",
            sha1 = sha1,
            sha256 = sha256,
            keyAlgorithm = keyAlgorithm,
            keySizeBits = keySizeBits,
            validFrom = validFrom,
            validUntil = validUntil,
        )
    }

    private fun playIdentity(certificate: Availability<CertificateFingerprints>): SigningIdentity {
        val fingerprints = certificate.valueOrNull()
        return SigningIdentity(
            kind = IdentityKind.PlayAppSigning,
            label = "Play app signing key",
            source = "Play Developer API",
            sha1 = fingerprints?.sha1,
            sha256 = fingerprints?.sha256,
            unreadableReason = certificate.reasonOrNull(),
        )
    }

    private fun readGoogleServices(project: AndroidProject, module: GradleModule): Availability<GoogleServicesConfig> {
        val directory = module.directory ?: return unavailable("${module.path} has no directory.")
        val relative = "$directory/google-services.json"
        val path = fileSystem.resolve(project.rootPath, relative)
        if (!fileSystem.exists(path)) {
            return unavailable(
                "This project has no $relative, so Servante cannot tell which fingerprints Firebase " +
                    "knows about.",
            )
        }
        return when (val text = fileSystem.readText(path)) {
            is Availability.Unavailable -> unavailable(text.reason)
            is Availability.Available -> googleServicesReader.read(relative, text.value)
        }
    }

    private fun collectFindings(
        matrix: FingerprintMatrix,
        declarations: SigningDeclarations,
        identities: List<SigningIdentity>,
        module: GradleModule,
    ): List<Finding> {
        val buildFile = "${module.directory}/build.gradle.kts"
        return buildList {
            addAll(SigningRules.missingRegistrations(matrix))
            addAll(SigningRules.unverifiableRegistrations(matrix))
            addAll(SigningRules.committedSecrets(declarations, buildFile))
            addAll(SigningRules.releaseSignedWithDebugKey(declarations))
            SigningRules.missingReleaseConfig(declarations, buildFile)?.let { add(it) }
            identities.forEach { identity ->
                SigningRules.certificateValidity(identity, clock)?.let { add(it) }
                addAll(SigningRules.weakAlgorithms(identity, signatureAlgorithm = null))
            }
        }
    }

    /** How many checks were run, so a healthy result can say so instead of showing an empty screen. */
    private fun checksRun(identities: List<SigningIdentity>, matrix: FingerprintMatrix): Int =
        identities.size * matrix.consumers.size + identities.size * CHECKS_PER_IDENTITY

    private companion object {
        private const val TOTAL_STEPS = 4
        private const val READ_KEYSTORES_STEP = 3
        private const val CHECKS_PER_IDENTITY = 2
        private val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")
    }
}

/** Debug, upload or something else, from where the keystore came from and which alias was read. */
private fun KeystoreCandidate.kind(declaredAlias: String?): IdentityKind = when {
    origin == KeystoreOrigin.AndroidStudioDebug -> IdentityKind.Debug
    configName?.contains("debug", ignoreCase = true) == true -> IdentityKind.Debug
    declaredAlias?.contains("upload", ignoreCase = true) == true -> IdentityKind.Upload
    configName?.contains("release", ignoreCase = true) == true -> IdentityKind.Upload
    else -> IdentityKind.Other
}

private fun KeystoreCandidate.label(): String = when {
    origin == KeystoreOrigin.AndroidStudioDebug -> "Debug keystore"
    configName != null -> "$configName key"
    else -> "Added keystore"
}

/**
 * The application id, read from the module's build file.
 *
 * Servante does not evaluate the build, so a computed application id is not resolved: the caller falls
 * back to the project name and the matrix says which package it compared against, so the user can see
 * when it guessed wrong rather than being told a wrong answer confidently.
 */
private fun GradleModule.applicationId(project: AndroidProject, fileSystem: SigningFileSystem): String? {
    val directory = directory ?: return null
    val buildFile = listOf("build.gradle.kts", "build.gradle")
        .map { fileSystem.resolve(fileSystem.resolve(project.rootPath, directory), it) }
        .firstOrNull { fileSystem.exists(it) }
        ?: return null
    val text = (fileSystem.readText(buildFile) as? Availability.Available)?.value ?: return null
    return Regex("""applicationId\s*=?\s*['"]([^'"]+)['"]""").find(text)?.groupValues?.get(1)
        ?: Regex("""namespace\s*=?\s*['"]([^'"]+)['"]""").find(text)?.groupValues?.get(1)
}
