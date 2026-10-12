package app.servante.core.flags

import app.servante.core.ToolId

/**
 * What is finished enough to show.
 *
 * Servante is built one tool per milestone and `main` always has to be shippable, so a tool that is
 * half built is hidden rather than shown broken. The flag is the switch: a tool that is off does not
 * appear in the sidebar, on the Overview or in the CLI.
 *
 * Flags are removed when a tool ships, not left to accumulate. A flag that has been on for a release is
 * dead code holding a branch open.
 */
public enum class Feature(public val tool: ToolId?, public val finishedIn: String?) {
    Signing(ToolId.Signing, finishedIn = "0.1"),
    Compose(ToolId.Compose, finishedIn = null),
    Gradle(ToolId.Gradle, finishedIn = null),
    SdkDisk(ToolId.SdkDisk, finishedIn = null),
    Preflight(ToolId.Preflight, finishedIn = null),
    Releases(ToolId.Releases, finishedIn = null),
    Reminders(ToolId.Reminders, finishedIn = null),
    Accessibility(ToolId.Accessibility, finishedIn = null),
    Security(ToolId.Security, finishedIn = null),
    AiReview(ToolId.AiReview, finishedIn = null),

    /** The component gallery, which is a development aid and never in a release build. */
    ComponentGallery(tool = null, finishedIn = null),
    ;

    /** A tool is on when it has shipped. Unfinished ones are shown only when explicitly enabled. */
    public val enabledByDefault: Boolean get() = finishedIn != null
}

/**
 * Whether a feature is on.
 *
 * An interface so tests and demo mode set flags directly, and so the implementation can later read an
 * override from settings or from an environment variable without any caller changing.
 */
public fun interface FeatureFlags {
    public fun isEnabled(feature: Feature): Boolean

    public companion object {
        /** What a release build does: shipped tools on, everything else off. */
        public val Default: FeatureFlags = FeatureFlags { it.enabledByDefault }

        /** Everything on, for development and for the screenshots in `design/implemented/`. */
        public val AllEnabled: FeatureFlags = FeatureFlags { true }

        public fun of(vararg enabled: Feature): FeatureFlags {
            val set = enabled.toSet()
            return FeatureFlags { it in set }
        }
    }
}

/** The tools to show, in sidebar order, filtered by what is on. */
public fun FeatureFlags.enabledTools(): List<ToolId> =
    ToolId.entries.filter { tool -> Feature.entries.any { it.tool == tool && isEnabled(it) } }
