package app.servante.cli

import app.servante.core.Availability
import app.servante.core.ToolState
import app.servante.core.project.ProjectDetector
import app.servante.core.unavailable
import app.servante.tools.signing.GoogleServicesReader
import app.servante.tools.signing.KeystorePasswordProvider
import app.servante.tools.signing.KeystoreReader
import app.servante.tools.signing.PlayAppSigningCertificates
import app.servante.tools.signing.RealSigningFileSystem
import app.servante.tools.signing.SigningConfigReader
import app.servante.tools.signing.SigningFileSystem
import app.servante.tools.signing.SigningMarkdown
import app.servante.tools.signing.SigningReport
import app.servante.tools.signing.SigningTool
import kotlin.time.Clock

/**
 * Runs the Signing tool over a folder and prints its Markdown diagnosis.
 *
 * This exists so M1 can be exercised against a real project before the screens are built: it is the
 * same tool, the same readers and the same export the Signing screen will call, with a terminal where
 * the screen will be. It is not `servante doctor` (M9), which reports every tool in several formats and
 * grades the exit code by severity.
 *
 * The diagnosis goes to [out] and everything else to [progress], so redirecting standard output gives a
 * clean Markdown file.
 */
internal class SigningCheckCommand(
    private val detector: ProjectDetector,
    private val passwords: KeystorePasswordProvider,
    private val out: Appendable,
    private val progress: Appendable,
    clock: Clock = Clock.System,
    fileSystem: SigningFileSystem = RealSigningFileSystem(),
    playCertificates: PlayAppSigningCertificates = NO_PLAY_ACCOUNT,
) {
    private val tool = SigningTool(
        fileSystem = fileSystem,
        keystoreReader = KeystoreReader(),
        googleServicesReader = GoogleServicesReader(),
        signingConfigReader = SigningConfigReader(),
        passwordProvider = passwords,
        playCertificates = playCertificates,
        clock = clock,
    )

    /** @return the process exit code. */
    suspend fun run(projectPath: String, userAddedKeystores: List<String> = emptyList()): Int {
        val project = when (val detected = detector.detect(projectPath)) {
            is Availability.Unavailable -> {
                progress.appendLine("Nothing was checked: ${detected.reason}")
                return PROJECT_UNREADABLE
            }

            is Availability.Available -> detected.value
        }
        progress.appendLine("${project.name}: ${project.modules.size} modules, read from ${project.rootPath}")

        var exitCode = OK
        tool.check(project, userAddedKeystores).collect { state ->
            when (state) {
                is ToolState.Loading ->
                    progress.appendLine(
                        "[${state.progress.step}/${state.progress.totalSteps}] ${state.progress.message}",
                    )

                is ToolState.Healthy -> out.append(render(state.result, project.name))

                is ToolState.Findings -> out.append(render(state.result, project.name))

                is ToolState.Unavailable -> {
                    progress.appendLine("Nothing was checked: ${state.reason}")
                    exitCode = CHECK_UNAVAILABLE
                }

                is ToolState.Error -> {
                    progress.appendLine("The check stopped: ${state.message}")
                    state.detail?.let { progress.appendLine(it) }
                    if (state.nothingChanged) progress.appendLine("Nothing in the project was changed.")
                    exitCode = CHECK_FAILED
                }

                ToolState.FirstRun -> Unit
            }
        }
        return exitCode
    }

    private fun render(report: SigningReport, projectName: String): String = SigningMarkdown.render(report, projectName)

    internal companion object {
        const val OK: Int = 0
        const val USAGE: Int = 2
        const val PROJECT_UNREADABLE: Int = 3
        const val CHECK_UNAVAILABLE: Int = 4
        const val CHECK_FAILED: Int = 5

        /**
         * No Play account is reachable from a terminal in M1: connecting a service account is M6, and
         * the certificate it would fetch is reported as unavailable with that reason rather than left
         * blank. See `docs/notes/play-signing-cert.md`.
         */
        private val NO_PLAY_ACCOUNT = PlayAppSigningCertificates {
            unavailable(
                "No Google Play service account is connected, so the Play app signing certificate " +
                    "could not be read. Copy it from the Play Console, under Release, Setup, App " +
                    "signing.",
            )
        }
    }
}
