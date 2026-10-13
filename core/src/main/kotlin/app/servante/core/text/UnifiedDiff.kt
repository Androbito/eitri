package app.servante.core.text

/**
 * The preview shown in a confirmation dialog: what the file says now against what it will say.
 *
 * It compares from both ends and reports the block in the middle that differs, with a few lines of
 * context, which is exactly the shape the dialogs in `design/screens/Dialogs.dc.html` draw. That is all
 * Servante's own writes need, because each one changes a value or adds a file: a general line-difference
 * algorithm would produce a prettier diff for an unrelated rewrite and is not worth carrying for that.
 *
 * It is a preview only. The gate writes the new content, not this diff, so the two cannot disagree.
 */
public object UnifiedDiff {
    private const val CONTEXT_LINES = 3

    public fun between(path: String, before: String, after: String): String {
        if (before == after) return ""
        val beforeLines = before.lines()
        val afterLines = after.lines()

        var prefix = 0
        while (
            prefix < beforeLines.size && prefix < afterLines.size &&
            beforeLines[prefix] == afterLines[prefix]
        ) {
            prefix++
        }
        var suffix = 0
        while (
            suffix < beforeLines.size - prefix && suffix < afterLines.size - prefix &&
            beforeLines[beforeLines.size - 1 - suffix] == afterLines[afterLines.size - 1 - suffix]
        ) {
            suffix++
        }

        val removed = beforeLines.subList(prefix, beforeLines.size - suffix)
        val added = afterLines.subList(prefix, afterLines.size - suffix)
        val leading = beforeLines.subList((prefix - CONTEXT_LINES).coerceAtLeast(0), prefix)
        val trailingFrom = beforeLines.size - suffix
        val trailing = beforeLines.subList(
            trailingFrom.coerceAtMost(beforeLines.size),
            (trailingFrom + CONTEXT_LINES).coerceAtMost(beforeLines.size),
        )

        return buildString {
            appendLine("--- $path")
            appendLine("+++ $path")
            leading.forEach { appendLine(" $it") }
            removed.withoutTrailingNewlineArtefact().forEach { appendLine("-$it") }
            added.withoutTrailingNewlineArtefact().forEach { appendLine("+$it") }
            trailing.withoutTrailingNewlineArtefact().forEach { appendLine(" $it") }
        }.trimEnd('\n')
    }

    /**
     * Drops the empty element that splitting a file ending in a newline leaves behind, so a diff does
     * not show a blank line that is not in the file.
     */
    private fun List<String>.withoutTrailingNewlineArtefact(): List<String> =
        if (size > 1 && last().isEmpty()) dropLast(1) else filter { it.isNotEmpty() }
}
