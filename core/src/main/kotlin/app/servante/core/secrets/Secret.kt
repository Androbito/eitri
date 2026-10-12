package app.servante.core.secrets

/**
 * A value that must not reach a log, a file or a screen.
 *
 * Wrapping it is what stops the common accident: a data class holding a password prints it in every
 * stack trace and every `println`, and a captured process output containing it ends up in a bug report.
 * The value comes out only through [reveal], which is easy to find in review.
 */
@JvmInline
public value class Secret(private val value: String) {
    public fun reveal(): String = value

    public val isEmpty: Boolean get() = value.isEmpty()

    override fun toString(): String = "Secret(hidden)"
}

/**
 * How a matched secret is shown in a finding: the first and last few characters, never the middle.
 *
 * A value too short to mask shows none of itself, because showing most of a short key is showing the key.
 */
public fun mask(value: String, keep: Int = 4): String {
    require(keep > 0) { "keep must be positive, was $keep." }
    if (value.length <= keep * 2) return "………"
    return value.take(keep) + "…" + value.takeLast(keep)
}
