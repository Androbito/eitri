package app.servante.storage.project

import app.servante.core.text.GradleScript

/** What Servante needs from a `settings.gradle(.kts)` file. */
public data class ParsedSettingsScript(public val rootProjectName: String?, public val modulePaths: List<String>)

/**
 * Reads the module list out of a settings script without executing it.
 *
 * Servante never runs the user's build to answer a question it can answer by reading, so this is text
 * analysis, and it is honest about its limits: it finds `include` statements in both DSLs and ignores
 * everything else. A settings file that computes its module list in a loop, or moves a module with
 * `project(":x").projectDir`, is not fully described by this parser. The detector reports what it could
 * not resolve rather than guessing: see [FileSystemProjectDetector].
 */
public object SettingsScriptParser {
    private val ROOT_NAME = Regex("""rootProject\.name\s*=\s*['"]([^'"]+)['"]""")
    private val COLON_PATH = Regex("""['"](:[^'"]+)['"]""")
    private const val INCLUDE = "include"

    public fun parse(script: String): ParsedSettingsScript {
        val code = GradleScript.stripComments(script)
        val paths = linkedSetOf<String>()
        for (arguments in includeArguments(code)) {
            COLON_PATH.findAll(arguments).forEach { paths += it.groupValues[1] }
        }
        return ParsedSettingsScript(
            rootProjectName = ROOT_NAME.find(code)?.groupValues?.get(1),
            modulePaths = paths.toList(),
        )
    }

    /**
     * The argument text of every `include` statement, in either DSL.
     *
     * Scanned rather than matched with one regular expression: an include can be a parenthesised call
     * spanning several lines, or a Groovy statement with no parentheses that ends at the newline, and one
     * expression covering both was easy to write and hard to read. `includeBuild` and `includeFlat` are
     * skipped by requiring that the keyword ends where it is found.
     */
    private fun includeArguments(code: String): List<String> {
        val found = mutableListOf<String>()
        var at = code.indexOf(INCLUDE)
        while (at >= 0) {
            val afterKeyword = at + INCLUDE.length
            if (!code.isWordCharAt(at - 1) && !code.isWordCharAt(afterKeyword)) {
                found += code.argumentsAt(afterKeyword)
            }
            at = code.indexOf(INCLUDE, afterKeyword)
        }
        return found
    }

    private fun String.argumentsAt(afterKeyword: Int): String {
        var i = afterKeyword
        while (i < length && this[i].isWhitespace() && this[i] != '\n') i++
        return if (i < length && this[i] == '(') {
            readBalanced(i)
        } else {
            substring(i, indexOf('\n', i).takeIf { it >= 0 } ?: length)
        }
    }

    /** The text between the parenthesis at [openAt] and its match, nesting and quotes respected. */
    private fun String.readBalanced(openAt: Int): String {
        var depth = 0
        var quote: Char? = null
        var i = openAt
        while (i < length) {
            val c = this[i]
            when {
                quote != null -> if (c == quote) quote = null

                c == '\'' || c == '"' -> quote = c

                c == '(' -> depth++

                c == ')' -> {
                    depth--
                    if (depth == 0) return substring(openAt + 1, i)
                }
            }
            i++
        }
        return substring(openAt + 1)
    }

    private fun String.isWordCharAt(index: Int): Boolean {
        if (index < 0 || index >= length) return false
        return this[index].isLetterOrDigit() || this[index] == '_' || this[index] == '.'
    }
}
