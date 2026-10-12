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
 * Two rules, both there because the ends are shown for one reason only, so that a long API key can be
 * told apart from another one. Anything shorter has nothing to tell apart and shows none of itself; and
 * however long the value is, at most a quarter of it is ever revealed. Four characters from each end of
 * a nine-character password is most of the password.
 *
 * @param keep the most characters to take from each end, reduced when the value is too short for it.
 */
public fun mask(value: String, keep: Int = 4): String {
    require(keep > 0) { "keep must be positive, was $keep." }
    if (value.length < SHORTEST_WORTH_IDENTIFYING) return HIDDEN
    val revealed = minOf(keep, value.length / (QUARTER * 2))
    if (revealed == 0) return HIDDEN
    return value.take(revealed) + "…" + value.takeLast(revealed)
}

private const val HIDDEN = "………"
private const val QUARTER = 4

/** Below this, a value is not shown at all: there is nothing to identify and too little to hide behind. */
private const val SHORTEST_WORTH_IDENTIFYING = 12
