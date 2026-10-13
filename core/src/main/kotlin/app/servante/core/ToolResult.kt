package app.servante.core

/**
 * What a tool produces: the findings, plus how many checks passed so a healthy result can say
 * "Nothing wrong. 14 checks passed." rather than show an empty screen.
 *
 * Tool-specific data (a fingerprint matrix, a size breakdown) is carried by each tool's own result type,
 * which holds one of these. `core` stays free of tool detail.
 */
public data class ToolResult(
    public val tool: ToolId,
    public val findings: List<Finding>,
    public val checksPassed: Int,
) {
    init {
        require(checksPassed >= 0) { "checksPassed cannot be negative, was $checksPassed." }
    }

    /** The worst severity among the findings, or [Severity.Healthy] when there are none. */
    public val worstSeverity: Severity get() = findings.map { it.severity }.worst()

    public fun count(severity: Severity): Int = findings.count { it.severity == severity }

    /** Findings worth the user's attention, worst first, which is the order the designs list them in. */
    public fun actionable(): List<Finding> =
        findings.filter { it.severity != Severity.Healthy }.sortedBy { it.severity }
}
