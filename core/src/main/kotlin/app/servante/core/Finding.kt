package app.servante.core

/** Stable identifier of a finding's kind, used for baselines, for tests and for documentation links. */
@JvmInline
public value class FindingId(public val value: String) {
    init {
        require(value.isNotBlank()) { "A finding id cannot be blank." }
    }

    override fun toString(): String = value
}

/**
 * A place in the user's project, rendered the way the designs show it: `app/build.gradle.kts:42`.
 *
 * @param path relative to the project root, so a finding can be shown and copied without leaking the
 *   absolute path of someone's home directory.
 */
public data class SourceLocation(
    public val path: String,
    public val line: Int? = null,
    public val column: Int? = null,
) {
    init {
        require(path.isNotBlank()) { "A location needs a path." }
        require(line == null || line > 0) { "Line numbers start at 1, was $line." }
        require(column == null || column > 0) { "Column numbers start at 1, was $column." }
        require(column == null || line != null) { "A column without a line cannot be rendered." }
    }

    public fun render(): String = buildString {
        append(path)
        if (line != null) {
            append(':').append(line)
            if (column != null) append(':').append(column)
        }
    }
}

/**
 * A single piece of what the tool actually read, shown under the explanation so the user can check the
 * finding rather than take it on trust.
 *
 * @param value never a whole secret. A scanner that matched a key shows the first and last characters
 *   only; see [app.servante.core.secrets.SecretStore] and the secret scanner's masking rule.
 */
public data class Evidence(public val label: String, public val value: String)

/** A standard a finding maps to: a WCAG success criterion, an MASVS control, a CWE. */
public data class StandardRef(public val id: String, public val title: String? = null) {
    init {
        require(id.isNotBlank()) { "A standard reference needs an id." }
    }

    /** What the chip shows: `WCAG 1.4.3 Contrast`, or just the id when there is no title. */
    public fun render(): String = if (title == null) id else "$id $title"
}

/** What to do about a finding. A diff when the change is mechanical, words when it is a judgement. */
public sealed interface Fix {
    /** A unified diff. Servante never applies one without going through the write gate. */
    public data class Patch(public val unifiedDiff: String, public val summary: String) : Fix

    public data class Guidance(public val text: String) : Fix
}

/**
 * One thing a tool found. Every tool in Servante reports in this shape so the Overview screen, the CLI
 * and the exports can treat them all the same way.
 *
 * @param explanation why it matters and what happens if it is left alone, in the user's language.
 * @param standards the standards this maps to, rendered as the chips in the designs.
 */
public data class Finding(
    public val id: FindingId,
    public val severity: Severity,
    public val title: String,
    public val explanation: String,
    public val location: SourceLocation? = null,
    public val evidence: List<Evidence> = emptyList(),
    public val standards: List<StandardRef> = emptyList(),
    public val fix: Fix? = null,
) {
    init {
        require(title.isNotBlank()) { "A finding needs a title: ${id.value}" }
        require(explanation.isNotBlank()) { "A finding needs an explanation: ${id.value}" }
    }
}
