package app.servante.ui.text

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import app.servante.core.Severity
import app.servante.core.ToolId
import app.servante.core.net.NetworkDestination
import app.servante.core.settings.Language

/**
 * Every word the UI shows, in one interface per language.
 *
 * An interface rather than a key lookup: a missing translation is a compile error, so a release cannot
 * ship a screen with `settings.title` showing where a title should be. There is no string table to keep
 * in step and no runtime fallback that silently hides a gap.
 *
 * English and French from the first release, as the roadmap requires. Arabic, which needs
 * right-to-left layout, is in the "later" section of the roadmap and is not started here.
 */
public interface ServanteStrings {
    public val language: Language

    // Shell and navigation
    public val appName: String
    public val navigationLabel: String
    public val settings: String
    public val openProject: String
    public val changeProject: String

    // Welcome
    public val welcomeHeadline: String
    public val welcomeBody: String
    public val openProjectFolder: String
    public val exploreWithDemoData: String
    public val welcomeFooter: String
    public val recentProjects: String
    public val noRecentProjects: String

    // Overview
    public val overview: String
    public val needsAttention: String
    public val everythingElse: String
    public val checkEverythingAgain: String
    public val nothingCheckedYet: String

    // Tool states
    public val firstRunTitle: String
    public val loading: String
    public val healthy: String
    public val unavailable: String
    public val error: String
    public val nothingWasChanged: String
    public val tryAgain: String
    public val openFullOutput: String
    public val checkNow: String
    public val checkAgain: String

    // Common actions
    public val cancel: String
    public val copy: String
    public val copied: String
    public val openInEditor: String
    public val copyDiff: String
    public val exportAsMarkdown: String
    public val findings: String

    // Settings
    public val general: String
    public val appearance: String
    public val appearanceFollowSystem: String
    public val appearanceLight: String
    public val appearanceDark: String
    public val languageLabel: String
    public val checkForUpdatesAtLaunch: String
    public val paths: String
    public val androidSdkPath: String
    public val gradleJdkPath: String
    public val googlePlayAccounts: String
    public val connectServiceAccount: String
    public val remove: String
    public val aiProviders: String
    public val notSet: String
    public val secretsStoredInKeychain: String
    public val privacyTitle: String
    public val privacyBody: String
    public val privacyDestination: String
    public val privacyWhy: String
    public val privacyWhen: String
    public val sourceOnGitHub: String
    public val openSourceUnderApache: String

    // Write confirmations
    public val changeWillBeRecorded: String
    public val nothingIsCommittedForYou: String

    // Signing
    public val signingQuestion: String
    public val verifyABuild: String
    public val fingerprintMatrix: String
    public val signingIdentity: String
    public val selectedIdentity: String
    public val source: String
    public val algorithm: String
    public val validFrom: String
    public val validUntil: String
    public val matchStateMatch: String
    public val matchStateMissing: String
    public val matchStateUnknown: String
    public val matchStateNotUsed: String
    public val keystorePasswordTitle: String
    public val keystorePasswordBody: String
    public val keystorePassword: String
    public val alias: String
    public val skipThisKeystore: String
    public val readKeystore: String
    public val copyFingerprints: String

    /** "Nothing wrong. 14 checks passed." */
    public fun healthyWithChecks(checksPassed: Int): String

    /** "Reading 3 keystores, step 2 of 4." */
    public fun loadingStep(message: String, step: Int, totalSteps: Int): String

    /** "Fjord Notes, 3 tools need attention" */
    public fun projectSummary(projectName: String, toolsNeedingAttention: Int): String

    /** "Checked 2 minutes ago" */
    public fun checkedAgo(relativeTime: String): String

    public fun toolName(tool: ToolId): String

    public fun severityName(severity: Severity): String

    /** The reason column of the privacy table. */
    public fun privacyReason(destination: NetworkDestination): String

    /** The when column of the privacy table. */
    public fun privacyWhen(destination: NetworkDestination): String

    public fun destinationLabel(destination: NetworkDestination): String
}

public val LocalStrings: ProvidableCompositionLocal<ServanteStrings> =
    staticCompositionLocalOf { EnglishStrings }

/** @param language null follows the system language, falling back to English. */
public fun stringsFor(language: Language?): ServanteStrings = when (language ?: Language.forTag(systemLanguageTag())) {
    Language.English -> EnglishStrings
    Language.French -> FrenchStrings
}

private fun systemLanguageTag(): String = java.util.Locale.getDefault().language
