package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.Fix
import app.servante.core.Severity
import app.servante.core.ToolState
import app.servante.core.available
import app.servante.core.fakes.FixedClock
import app.servante.core.secrets.Secret
import app.servante.core.unavailable
import app.servante.storage.project.FileSystemProjectDetector
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The whole tool, over the Fjord Notes fixture, which is the case the designs show: Firebase knows the
 * debug and upload certificates and not the Play one, and the release password is committed.
 *
 * Behaviours:
 * - the run emits progress and then a findings state
 * - the headline finding is the Play signing certificate missing from Firebase, critical
 * - the committed keystore password is reported, without the password in it
 * - the fingerprints in the matrix are the real ones from the fixture keystores
 * - a skipped keystore password leaves that key unreadable with the reason, and the run still finishes
 * - no Play connection leaves the Play column unknown and does not invent a certificate
 * - a project with no application module is unavailable with a reason
 * - the Markdown export contains the matrix and the full fingerprints
 */
class SigningToolTest {
    private val fixtures: Path = Path.of(
        requireNotNull(System.getProperty("servante.fixtures")) { "set by the test task" },
    )
    private val clock = FixedClock(Instant.parse("2026-10-12T20:30:00Z"))
    private fun fingerprintOf(hex: String) = Fingerprint(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    /** What the Play Developer API gives: SHA-256 and nothing else. */
    private val playFromApi = CertificateFingerprints(
        sha256 = fingerprintOf(
            "f2773db05ac10e946bd8237fe0419ca538d26f0be714" +
                "85ca529e307db6481" + "9ac",
        ),
    )

    /** What is known after downloading a generated APK and reading the certificate out of it. */
    private val playFromGeneratedApk = playFromApi.copy(
        sha1 = fingerprintOf("9b04e16c2fa855d0713ebc480af6129dc763e52b"),
    )

    /** The fixture keystore passwords, as a user would type them when asked. */
    private val knownPasswords = mapOf(
        "release.jks" to "fjord-release-2024",
        "debug.keystore" to "android",
        "legacy.p12" to "fjord-legacy",
    )

    private fun tool(
        passwordProvider: KeystorePasswordProvider = KeystorePasswordProvider { path ->
            knownPasswords.entries.firstOrNull { path.endsWith(it.key) }?.value?.let(::Secret)
        },
        playCertificates: PlayAppSigningCertificates = PlayAppSigningCertificates {
            playFromApi.available()
        },
        homeDirectory: String = fixtures.resolve("fjord-notes").toString(),
    ) = SigningTool(
        fileSystem = RealSigningFileSystem(homePath = homeDirectory),
        keystoreReader = KeystoreReader(),
        googleServicesReader = GoogleServicesReader(),
        signingConfigReader = SigningConfigReader(),
        passwordProvider = passwordProvider,
        playCertificates = playCertificates,
        clock = clock,
    )

    private fun fjordNotes() = (
        FileSystemProjectDetector().detect(fixtures.resolve("fjord-notes").toString())
            as Availability.Available
        ).value

    @Test
    fun `the run emits progress and then findings`() = runTest {
        val states = tool().check(fjordNotes()).toList()

        assertTrue(states.size > 1, "expected progress before the result, got $states")
        assertTrue(states.dropLast(1).all { it is ToolState.Loading })
        assertTrue(states.last() is ToolState.Findings, "was ${states.last()}")
    }

    @Test
    fun `with only the API, the Play row cannot be compared and the tool says so`() = runTest {
        val report = report()

        // google-services.json records SHA-1 and the API gives SHA-256, so there is nothing to compare.
        assertEquals(
            MatchState.Unknown,
            report.matrix.cell(IdentityKind.PlayAppSigning, Consumer.Firebase).state,
        )
        val finding = report.result.findings
            .single { it.id.value == "signing.play-registration-unverified" }
        assertEquals(Severity.Warning, finding.severity)
        assertTrue("cannot be compared" in finding.explanation, finding.explanation)
        assertTrue(finding.fix is Fix.Guidance)
        // The upload key must not be reported as missing from Play: that is how Play App Signing works.
        assertTrue(
            report.result.findings.none { "Play does not know" in it.title },
            "had ${report.result.findings.map { it.title }}",
        )
    }

    @Test
    fun `with both fingerprints known, the missing Firebase registration is critical`() = runTest {
        val report = report(
            tool(playCertificates = PlayAppSigningCertificates { playFromGeneratedApk.available() }),
        )

        assertEquals(
            MatchState.Missing,
            report.matrix.cell(IdentityKind.PlayAppSigning, Consumer.Firebase).state,
        )
        val critical = report.result.findings.filter { it.severity == Severity.Critical }
        assertTrue(critical.isNotEmpty(), "had ${report.result.findings.map { it.title }}")
        assertTrue(
            critical.any { "Play app signing" in it.title && "Firebase" in it.title },
            "titles were ${critical.map { it.title }}",
        )
    }

    @Test
    fun `the committed keystore password is reported without showing the password`() = runTest {
        val report = report()

        // Both the store password and the key password are literals in the fixture, so there are two.
        val findings = report.result.findings.filter { "password committed" in it.title.lowercase() }
        assertEquals(2, findings.size)
        assertTrue(findings.all { it.severity == Severity.Warning })
        assertTrue(findings.all { it.evidence.isNotEmpty() })
        assertTrue(findings.all { finding -> finding.evidence.none { "fjord-release-2024" in it.value } })
    }

    @Test
    fun `the upload key in the matrix is the real fixture fingerprint`() = runTest {
        val report = report()

        val upload = report.identities.first { it.kind == IdentityKind.Upload }
        assertEquals("8914B81777BA9A35C1ACE9BED6EE805E2CD35731", upload.sha1?.plainLowercase?.uppercase())
        assertEquals(MatchState.Match, report.matrix.cell(IdentityKind.Upload, Consumer.Firebase).state)
    }

    @Test
    fun `a skipped password leaves that key unreadable and the run still finishes`() = runTest {
        val report = report(
            tool(
                passwordProvider = KeystorePasswordProvider { path ->
                    if (path.endsWith("release.jks")) null else Secret("android")
                },
            ),
        )

        val upload = report.identities.firstOrNull { it.unreadableReason != null }
        assertTrue(upload != null, "expected an unreadable identity")
        assertTrue("password" in upload.unreadableReason.orEmpty().lowercase())
    }

    @Test
    fun `no Play connection leaves the Play column unknown`() = runTest {
        val report = report(
            tool(
                playCertificates = PlayAppSigningCertificates {
                    unavailable("No Play account is connected, so releases cannot be read.")
                },
            ),
        )

        val cell = report.matrix.cell(IdentityKind.PlayAppSigning, Consumer.Play)
        assertEquals(MatchState.Unknown, cell.state)
        assertTrue("No Play account" in cell.reason.orEmpty())
        assertTrue(
            report.identities.first { it.kind == IdentityKind.PlayAppSigning }.sha256 == null,
            "a Play certificate must not be invented when there is no connection",
        )
    }

    @Test
    fun `a project with no application module is unavailable`() = runTest {
        val groovy = (
            FileSystemProjectDetector().detect(fixtures.resolve("fjord-notes").toString())
                as Availability.Available
            ).value
        val libraryOnly = groovy.copy(modules = groovy.modules.filter { it.path != ":app" })

        val states = tool().check(libraryOnly).toList()

        val last = states.last()
        assertTrue(last is ToolState.Unavailable, "was $last")
        assertTrue("application plugin" in last.reason)
    }

    @Test
    fun `the Markdown export carries the matrix and the full fingerprints`() = runTest {
        val report = report(
            tool(playCertificates = PlayAppSigningCertificates { playFromGeneratedApk.available() }),
        )

        val markdown = SigningMarkdown.render(report, "Fjord Notes")

        assertTrue("# Signing diagnosis: Fjord Notes" in markdown)
        assertTrue("## Fingerprint matrix" in markdown)
        assertTrue("89:14:B8:17:77:BA:9A:35:C1:AC:E9:BE:D6:EE:80:5E:2C:D3:57:31" in markdown)
        assertTrue("**missing**" in markdown)
        assertTrue("app.fjord.notes" in markdown)
        // The export must never carry a secret, even a masked one's source value.
        assertTrue("fjord-release-2024" !in markdown)
    }

    private suspend fun report(tool: SigningTool = tool()): SigningReport {
        val states = tool.check(fjordNotes()).toList()
        return when (val last = states.last()) {
            is ToolState.Findings -> last.result
            is ToolState.Healthy -> last.result
            else -> error("expected a result, got $last")
        }
    }
}

/**
 * A project whose build file declares many signing configs, each with literal passwords, produces more
 * findings than the tool counts comparisons. The count of passed checks must not go negative, because
 * `ToolResult` rejects that and the tool would crash on the project it is meant to help with.
 */
class SigningToolManyFindingsTest {
    private val fixtures: Path = Path.of(
        requireNotNull(System.getProperty("servante.fixtures")) { "set by the test task" },
    )

    @Test
    fun `a project with more findings than comparisons still reports`() = runTest {
        val root = Files.createTempDirectory("servante-many")
        root.resolve("settings.gradle.kts").writeText("rootProject.name = \"many\"\ninclude(\":app\")\n")
        root.resolve("app").createDirectories()
        val configs = (1..12).joinToString("\n") { index ->
            """
            |        create("release$index") {
            |            storeFile = file("keystore$index.jks")
            |            storePassword = "password$index"
            |            keyAlias = "alias$index"
            |            keyPassword = "password$index"
            |        }
            """.trimMargin()
        }
        root.resolve("app/build.gradle.kts").writeText(
            """
            plugins { id("com.android.application") }

            android {
                namespace = "app.many"
                defaultConfig { applicationId = "app.many" }
                signingConfigs {
            $configs
                }
            }
            """.trimIndent(),
        )

        val tool = SigningTool(
            fileSystem = RealSigningFileSystem(homePath = root.toString()),
            keystoreReader = KeystoreReader(),
            googleServicesReader = GoogleServicesReader(),
            signingConfigReader = SigningConfigReader(),
            passwordProvider = KeystorePasswordProvider { null },
            playCertificates = PlayAppSigningCertificates { unavailable("No Play account.") },
            clock = FixedClock(Instant.parse("2026-10-12T20:30:00Z")),
        )
        val project = (FileSystemProjectDetector().detect(root.toString()) as Availability.Available).value

        val states = tool.check(project).toList()

        val last = states.last()
        assertTrue(last is ToolState.Findings, "was $last")
        val result = last.result.result
        assertEquals(24, result.findings.count { "password committed" in it.title.lowercase() })
        assertTrue(result.checksPassed >= 0)
    }
}
