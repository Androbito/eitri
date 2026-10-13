package app.servante.core

/**
 * The tools Servante ships, in the order the sidebar lists them in `design/screens/Main.dc.html`.
 *
 * @param key the stable identifier used in settings, the audit log and the CLI. Renaming a tool's label
 *   must not change its key, or a user's history stops matching.
 */
public enum class ToolId(public val key: String) {
    Signing("signing"),
    Compose("compose"),
    Gradle("gradle"),
    SdkDisk("sdk"),
    Preflight("preflight"),
    Releases("releases"),
    Reminders("reminders"),
    Accessibility("a11y"),
    Security("security"),
    AiReview("ai"),
    ;

    public companion object {
        public fun byKey(key: String): ToolId? = entries.firstOrNull { it.key == key }
    }
}
