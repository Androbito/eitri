package app.servante.core.net

import app.servante.core.ToolId

/**
 * Somewhere Servante is allowed to connect to.
 *
 * @param reasonKey localisation key for the "Why" column of the privacy table. The registry holds keys
 *   rather than sentences so the table is translated like everything else.
 * @param requiresOptIn true when nothing is sent until the user turns it on or presses a button.
 */
public sealed interface NetworkDestination {
    public val label: String
    public val reasonKey: String
    public val timingKey: String
    public val tool: ToolId?
    public val requiresOptIn: Boolean

    /** A fixed host. Requests to this host and its subdomains are permitted. */
    public data class Host(
        public val host: String,
        override val reasonKey: String,
        override val timingKey: String,
        override val tool: ToolId?,
        override val requiresOptIn: Boolean,
    ) : NetworkDestination {
        override val label: String get() = host
    }

    /**
     * A destination the user configures, which is the AI provider's endpoint. Its host cannot be known
     * in advance, so it is checked against what the user entered rather than against this registry, and
     * nothing is sent without the user seeing the payload first.
     */
    public data class UserProvided(
        override val label: String,
        override val reasonKey: String,
        override val timingKey: String,
        override val tool: ToolId?,
        override val requiresOptIn: Boolean,
    ) : NetworkDestination
}

/**
 * Every network destination Servante can reach, in one place.
 *
 * Two things read this list: the privacy table in Settings, and the HTTP client, which refuses a request
 * to a host that is not here. That is the point. A new call to a new service does not work until it is
 * added here, which means the list the user is shown cannot fall out of step with what the app does.
 *
 * There is no telemetry, no analytics and no Servante server in this list, and there is nowhere else for
 * one to hide.
 */
public object NetworkDestinations {
    public val all: List<NetworkDestination> = listOf(
        NetworkDestination.Host(
            host = "androidpublisher.googleapis.com",
            reasonKey = "privacy.reason.play",
            timingKey = "privacy.when.play",
            tool = ToolId.Releases,
            requiresOptIn = true,
        ),
        NetworkDestination.Host(
            host = "oauth2.googleapis.com",
            reasonKey = "privacy.reason.googleAuth",
            timingKey = "privacy.when.play",
            tool = ToolId.Releases,
            requiresOptIn = true,
        ),
        NetworkDestination.Host(
            host = "maven.google.com",
            reasonKey = "privacy.reason.maven",
            timingKey = "privacy.when.maven",
            tool = ToolId.Gradle,
            requiresOptIn = false,
        ),
        NetworkDestination.Host(
            host = "repo.maven.apache.org",
            reasonKey = "privacy.reason.maven",
            timingKey = "privacy.when.maven",
            tool = ToolId.Gradle,
            requiresOptIn = false,
        ),
        NetworkDestination.Host(
            host = "api.osv.dev",
            reasonKey = "privacy.reason.osv",
            timingKey = "privacy.when.osv",
            tool = ToolId.Security,
            requiresOptIn = true,
        ),
        NetworkDestination.Host(
            host = "github.com",
            reasonKey = "privacy.reason.github",
            timingKey = "privacy.when.github",
            tool = null,
            requiresOptIn = true,
        ),
        NetworkDestination.Host(
            host = "api.github.com",
            reasonKey = "privacy.reason.github",
            timingKey = "privacy.when.github",
            tool = null,
            requiresOptIn = true,
        ),
        NetworkDestination.UserProvided(
            label = "privacy.destination.aiProvider",
            reasonKey = "privacy.reason.ai",
            timingKey = "privacy.when.ai",
            tool = ToolId.AiReview,
            requiresOptIn = true,
        ),
    )

    private val allowedHosts: List<String> =
        all.filterIsInstance<NetworkDestination.Host>().map { it.host.lowercase() }

    /**
     * Whether a request to this host is permitted.
     *
     * A subdomain of an allowed host passes, because Google and GitHub serve from several. The check is
     * on a dot boundary, so `evilgithub.com` does not pass as `github.com`.
     */
    public fun isAllowed(host: String): Boolean {
        if (host.isBlank()) return false
        val candidate = host.lowercase().trimEnd('.')
        return allowedHosts.any { allowed -> candidate == allowed || candidate.endsWith(".$allowed") }
    }
}
