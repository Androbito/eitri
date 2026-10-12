package app.servante.ui.text

import app.servante.core.Severity
import app.servante.core.ToolId
import app.servante.core.net.NetworkDestination
import app.servante.core.settings.Language

/** The source language. Wording comes from `design/screens/`, which is the source of truth for it. */
public object EnglishStrings : ServanteStrings {
    override val language: Language = Language.English

    override val appName: String = "Servante"
    override val navigationLabel: String = "Tools"
    override val settings: String = "Settings"
    override val openProject: String = "Open a project"
    override val changeProject: String = "Change project"

    override val welcomeHeadline: String = "Open a project and find out what is wrong with it."
    override val welcomeBody: String =
        "Servante reads your Android project and checks signing, Compose, Gradle, the SDK, your " +
            "bundles, Play releases, accessibility and security. It changes nothing unless you confirm."
    override val openProjectFolder: String = "Open a project folder"
    override val exploreWithDemoData: String = "Explore with demo data"
    override val welcomeFooter: String = "No account. No telemetry. Open source under Apache 2.0."
    override val recentProjects: String = "Recent projects"
    override val noRecentProjects: String = "No projects opened yet."

    override val overview: String = "Overview"
    override val needsAttention: String = "Needs attention"
    override val everythingElse: String = "Everything else"
    override val checkEverythingAgain: String = "Check everything again"
    override val nothingCheckedYet: String = "Nothing has been checked yet for this project."

    override val firstRunTitle: String = "First run"
    override val loading: String = "Loading"
    override val healthy: String = "Healthy"
    override val unavailable: String = "Unavailable"
    override val error: String = "Error"
    override val nothingWasChanged: String = "Nothing was changed."
    override val tryAgain: String = "Try again"
    override val openFullOutput: String = "Open the full output"
    override val checkNow: String = "Check now"
    override val checkAgain: String = "Check again"

    override val cancel: String = "Cancel"
    override val copy: String = "Copy"
    override val copied: String = "Copied"
    override val openInEditor: String = "Open in editor"
    override val copyDiff: String = "Copy diff"
    override val exportAsMarkdown: String = "Export diagnosis as Markdown"
    override val findings: String = "Findings"

    override val general: String = "General"
    override val appearance: String = "Appearance"
    override val appearanceFollowSystem: String = "Follow the system"
    override val appearanceLight: String = "Light"
    override val appearanceDark: String = "Dark"
    override val languageLabel: String = "Language"
    override val checkForUpdatesAtLaunch: String = "Check GitHub for a new version at launch"
    override val paths: String = "Paths"
    override val androidSdkPath: String = "Android SDK"
    override val gradleJdkPath: String = "JDK used to run Gradle"
    override val googlePlayAccounts: String = "Google Play accounts"
    override val connectServiceAccount: String = "Connect a service account"
    override val remove: String = "Remove"
    override val aiProviders: String = "AI providers"
    override val notSet: String = "not set"
    override val secretsStoredInKeychain: String =
        "Stored in your system keychain. Never written to a file or a log."
    override val privacyTitle: String = "Privacy: everything this app can contact"
    override val privacyBody: String =
        "This list is generated from the code. There is no other network access, no account and no " +
            "telemetry."
    override val privacyDestination: String = "Destination"
    override val privacyWhy: String = "Why"
    override val privacyWhen: String = "When"
    override val sourceOnGitHub: String = "Source code on GitHub"
    override val openSourceUnderApache: String = "Open source under Apache 2.0."

    override val changeWillBeRecorded: String = "This change will be recorded in the history."
    override val nothingIsCommittedForYou: String = "Nothing is committed for you."

    override val signingQuestion: String = "Why does sign-in work in debug and fail in release?"
    override val verifyABuild: String = "Verify a build"
    override val fingerprintMatrix: String = "Fingerprint matrix"
    override val signingIdentity: String = "Signing identity"
    override val selectedIdentity: String = "Selected identity"
    override val source: String = "Source"
    override val algorithm: String = "Algorithm"
    override val validFrom: String = "Valid from"
    override val validUntil: String = "Valid until"
    override val matchStateMatch: String = "Match"
    override val matchStateMissing: String = "Missing"
    override val matchStateUnknown: String = "Unknown"
    override val matchStateNotUsed: String = "Not used"
    override val keystorePasswordTitle: String = "Password for a keystore"
    override val keystorePasswordBody: String =
        "Servante needs it to read the certificates in this keystore. It is used once, kept in memory " +
            "only, and never stored."
    override val keystorePassword: String = "Keystore password"
    override val alias: String = "Alias"
    override val skipThisKeystore: String = "Skip this keystore"
    override val readKeystore: String = "Read keystore"
    override val copyFingerprints: String = "Copy fingerprints"

    override fun healthyWithChecks(checksPassed: Int): String = when (checksPassed) {
        0 -> "Nothing wrong."
        1 -> "Nothing wrong. 1 check passed."
        else -> "Nothing wrong. $checksPassed checks passed."
    }

    override fun loadingStep(message: String, step: Int, totalSteps: Int): String =
        "$message, step $step of $totalSteps."

    override fun projectSummary(projectName: String, toolsNeedingAttention: Int): String =
        when (toolsNeedingAttention) {
            0 -> "$projectName, nothing needs attention"
            1 -> "$projectName, 1 tool needs attention"
            else -> "$projectName, $toolsNeedingAttention tools need attention"
        }

    override fun checkedAgo(relativeTime: String): String = "Checked $relativeTime"

    override fun toolName(tool: ToolId): String = when (tool) {
        ToolId.Signing -> "Signing"
        ToolId.Compose -> "Compose"
        ToolId.Gradle -> "Gradle"
        ToolId.SdkDisk -> "SDK and disk"
        ToolId.Preflight -> "Preflight"
        ToolId.Releases -> "Releases"
        ToolId.Reminders -> "Reminders"
        ToolId.Accessibility -> "Accessibility"
        ToolId.Security -> "Security"
        ToolId.AiReview -> "AI review"
    }

    override fun severityName(severity: Severity): String = when (severity) {
        Severity.Critical -> "Critical"
        Severity.Warning -> "Warning"
        Severity.Info -> "Information"
        Severity.Healthy -> "Healthy"
    }

    override fun privacyReason(destination: NetworkDestination): String = when (destination.reasonKey) {
        "privacy.reason.play" ->
            "Play releases and signing certificate, with your service account"

        "privacy.reason.googleAuth" -> "Signing in the service account you connected"

        "privacy.reason.maven" -> "Latest versions of your dependencies"

        "privacy.reason.osv" -> "Known vulnerabilities. Sends package names and versions only"

        "privacy.reason.github" -> "New versions and refreshed compatibility data"

        "privacy.reason.ai" -> "The files you select for a review, with your key"

        else -> destination.reasonKey
    }

    override fun privacyWhen(destination: NetworkDestination): String = when (destination.timingKey) {
        "privacy.when.play" -> "When you open Releases or Signing"
        "privacy.when.maven" -> "When you open Gradle updates"
        "privacy.when.osv" -> "When you run a dependency check"
        "privacy.when.github" -> "At launch, only if you turn it on"
        "privacy.when.ai" -> "When you press Send"
        else -> destination.timingKey
    }

    override fun destinationLabel(destination: NetworkDestination): String = when (destination) {
        is NetworkDestination.Host -> destination.host
        is NetworkDestination.UserProvided -> "Your AI provider"
    }
}
