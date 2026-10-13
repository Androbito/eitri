package app.servante.core

/**
 * How bad a finding is. Declared worst first, so the natural order of the enum is the order the UI lists
 * findings in and `minOf` picks the worst.
 *
 * `Healthy` is a severity rather than the absence of one, because a tool reports what it checked and
 * found correct as well as what it found wrong: see the healthy rows on the Signing screen.
 */
public enum class Severity {
    Critical,
    Warning,
    Info,
    Healthy,
}

/** The worst severity in this collection, or [Severity.Healthy] when it is empty. */
public fun Iterable<Severity>.worst(): Severity = minOrNull() ?: Severity.Healthy
