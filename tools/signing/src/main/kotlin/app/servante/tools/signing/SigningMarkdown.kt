package app.servante.tools.signing

import app.servante.core.Severity
import app.servante.core.ToolResult

/**
 * The diagnosis as Markdown, which is the export button on the Signing screen.
 *
 * Why Markdown and not a screenshot: this report is pasted into an issue, a pull request or a message to
 * whoever has access to the Firebase console. It has to be readable as text, and the fingerprints have
 * to be copyable, which is why they are in code spans and given in full rather than abbreviated.
 */
public object SigningMarkdown {
    public fun render(report: SigningReport, projectName: String): String = buildString {
        appendLine("# Signing diagnosis: $projectName")
        appendLine()
        appendLine("Package: `${report.matrix.packageName}`")
        appendLine()
        appendVerdict(report.result)
        appendMatrix(report.matrix)
        appendIdentities(report.identities)
        appendFindings(report.result)
        appendLine("---")
        appendLine()
        appendLine(
            "Produced by Servante. Every value above was read from this project, from its keystores " +
                "and from the Play Developer API. Anything Servante could not read is marked unknown " +
                "with the reason, and nothing here is a default or a placeholder.",
        )
    }

    private fun StringBuilder.appendVerdict(result: ToolResult) {
        appendLine("## Verdict")
        appendLine()
        val critical = result.count(Severity.Critical)
        val warnings = result.count(Severity.Warning)
        appendLine(
            when {
                critical > 0 -> "**$critical critical** and $warnings other findings."
                warnings > 0 -> "**No critical findings.** $warnings to look at."
                else -> "**Nothing wrong.** ${result.checksPassed} checks passed."
            },
        )
        appendLine()
    }

    private fun StringBuilder.appendMatrix(matrix: FingerprintMatrix) {
        appendLine("## Fingerprint matrix")
        appendLine()
        appendLine("| Signing identity | SHA-1 | ${matrix.consumers.joinToString(" | ") { it.column() }} |")
        appendLine("| --- | --- | ${matrix.consumers.joinToString(" | ") { "---" }} |")
        matrix.identities.forEach { identity ->
            val cells = matrix.consumers.joinToString(" | ") { consumer ->
                matrix.cell(identity, consumer).state.word()
            }
            val sha1 = identity.sha1?.let { "`${it.formatted}`" } ?: "unknown"
            appendLine("| ${identity.label} | $sha1 | $cells |")
        }
        appendLine()
        val reasons = matrix.identities.flatMap { identity ->
            matrix.consumers.mapNotNull { consumer ->
                matrix.cell(identity, consumer).reason?.let { reason ->
                    "- ${identity.label}, ${consumer.column()}: $reason"
                }
            }
        }
        if (reasons.isNotEmpty()) {
            appendLine("Why:")
            appendLine()
            reasons.forEach { appendLine(it) }
            appendLine()
        }
    }

    private fun StringBuilder.appendIdentities(identities: List<SigningIdentity>) {
        appendLine("## Keys")
        appendLine()
        identities.forEach { identity ->
            appendLine("### ${identity.label}")
            appendLine()
            appendLine("- Source: ${identity.source}")
            identity.keyAlgorithm?.let { algorithm ->
                val size = identity.keySizeBits?.takeIf { it > 0 }?.let { " $it" }.orEmpty()
                appendLine("- Algorithm: $algorithm$size")
            }
            identity.validFrom?.let { appendLine("- Valid from: ${it.day()}") }
            identity.validUntil?.let { appendLine("- Valid until: ${it.day()}") }
            identity.sha1?.let { appendLine("- SHA-1: `${it.formatted}`") }
            identity.sha256?.let { appendLine("- SHA-256: `${it.formatted}`") }
            identity.unreadableReason?.let { appendLine("- Could not be read: $it") }
            appendLine()
        }
    }

    private fun StringBuilder.appendFindings(result: ToolResult) {
        val actionable = result.actionable()
        if (actionable.isEmpty()) return
        appendLine("## Findings")
        appendLine()
        actionable.forEach { finding ->
            appendLine("### ${finding.severity.word()}: ${finding.title}")
            appendLine()
            appendLine(finding.explanation)
            appendLine()
            finding.location?.let { appendLine("- Where: `${it.render()}`") }
            finding.evidence.forEach { evidence ->
                appendLine("- ${evidence.label}: `${evidence.value}`")
            }
            finding.standards.forEach { standard ->
                appendLine("- Standard: ${standard.render()}")
            }
            appendLine()
        }
    }

    private fun Consumer.column(): String = when (this) {
        Consumer.Firebase -> "Firebase"
        Consumer.OAuthClient -> "OAuth client"
        Consumer.Play -> "Play"
    }

    private fun MatchState.word(): String = when (this) {
        MatchState.Match -> "match"
        MatchState.Missing -> "**missing**"
        MatchState.Unknown -> "unknown"
        MatchState.NotUsed -> "not used"
    }

    private fun Severity.word(): String = when (this) {
        Severity.Critical -> "Critical"
        Severity.Warning -> "Warning"
        Severity.Info -> "Information"
        Severity.Healthy -> "Healthy"
    }

    private fun kotlin.time.Instant.day(): String = toString().substringBefore('T')
}
