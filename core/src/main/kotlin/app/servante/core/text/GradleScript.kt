package app.servante.core.text

import app.servante.core.text.Scan.copyChar
import app.servante.core.text.Scan.copyStringLiteral
import app.servante.core.text.Scan.skipBlockComment
import app.servante.core.text.Scan.skipToEndOfLine
import app.servante.core.text.Scan.startsBlockComment
import app.servante.core.text.Scan.startsLineComment

/**
 * Reading Gradle scripts as text, without executing them.
 *
 * Servante answers what it can by reading, because configuring a user's build costs minutes and fails
 * for reasons unrelated to the question. That makes these functions load-bearing, and it makes their
 * limits part of the contract: what is here finds literal declarations in both DSLs and nothing else. A
 * value computed at configuration time is reported as an expression Servante did not evaluate, never as
 * a value it guessed.
 *
 * Pure text handling with no I/O, which is why it sits in `core` and is shared by the tools and by the
 * project detector rather than written twice.
 */
public object GradleScript {
    /**
     * Removes line and block comments, leaving string literals untouched: a `//` inside a quoted path is
     * part of the path. Needed so a commented-out declaration is not read as a real one.
     */
    public fun stripComments(script: String): String {
        val out = StringBuilder(script.length)
        var i = 0
        while (i < script.length) {
            i = when {
                script.startsLineComment(i) -> script.skipToEndOfLine(i)
                script.startsBlockComment(i) -> script.skipBlockComment(i)
                script[i] == '\'' || script[i] == '"' -> script.copyStringLiteral(i, out)
                else -> script.copyChar(i, out)
            }
        }
        return out.toString()
    }

    /**
     * The body of the first block with this name, brace-balanced, or null when there is none.
     *
     * `blockBody("signingConfigs", script)` returns what is between the braces of
     * `signingConfigs { ... }`, nested blocks included.
     */
    public fun blockBody(name: String, script: String): String? {
        var at = script.indexOf(name)
        while (at >= 0) {
            val before = at - 1
            val standsAlone = before < 0 || !script[before].isNameChar()
            if (standsAlone) {
                var i = at + name.length
                while (i < script.length && script[i].isWhitespace()) i++
                if (i < script.length && script[i] == '{') {
                    return script.balancedBody(i, '{', '}')
                }
            }
            at = script.indexOf(name, at + name.length)
        }
        return null
    }

    /**
     * Every top-level `name { ... }` block inside [script], as name to body.
     *
     * This is how a `signingConfigs` or `buildTypes` body is split into its entries, which in the Groovy
     * DSL are written as bare names and in the Kotlin DSL as `create("name") { }` or `getByName("name")`.
     */
    public fun namedBlocks(script: String): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < script.length) {
            if (script[i] == '{') {
                val head = script.substring(0, i)
                val name = headName(head)
                val body = script.balancedBody(i, '{', '}')
                if (name != null && body != null) found += name to body
                i += (body?.length ?: 0) + 2
            } else {
                i++
            }
        }
        return found
    }

    /**
     * The name a block head declares: the quoted argument of `create("release")` or `getByName("x")`, or
     * the last bare identifier for the Groovy form `release { }`.
     */
    private fun headName(head: String): String? {
        val trimmed = head.trimEnd()
        // The last match, not the first: the head of a second block contains everything before it,
        // including the first block's own name.
        QUOTED_CALL_ARGUMENT.findAll(trimmed).lastOrNull()?.let { match ->
            if (match.range.last == trimmed.lastIndex) return match.groupValues[1]
        }
        return TRAILING_IDENTIFIER.find(trimmed)?.groupValues?.get(1)
    }

    /** The text between the delimiter at [openAt] and its match, quotes and nesting respected. */
    private fun String.balancedBody(openAt: Int, open: Char, close: Char): String? {
        var depth = 0
        var quote: Char? = null
        var i = openAt
        while (i < length) {
            val c = this[i]
            when {
                quote != null -> if (c == quote) quote = null

                c == '\'' || c == '"' -> quote = c

                c == open -> depth++

                c == close -> {
                    depth--
                    if (depth == 0) return substring(openAt + 1, i)
                }
            }
            i++
        }
        return null
    }

    private fun Char.isNameChar(): Boolean = isLetterOrDigit() || this == '_' || this == '.'

    private val QUOTED_CALL_ARGUMENT = Regex("""\(\s*['"]([^'"]+)['"]\s*\)""")
    private val TRAILING_IDENTIFIER = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*$""")
}

/**
 * Character-level scanning of a script: where a comment starts, how far it runs, how to copy a string
 * literal verbatim. Separate from [GradleScript] because these answer "what is at this index", while
 * GradleScript answers "what does this script declare".
 */
internal object Scan {
    fun String.startsLineComment(at: Int): Boolean = this[at] == '/' && at + 1 < length && this[at + 1] == '/'

    fun String.startsBlockComment(at: Int): Boolean = this[at] == '/' && at + 1 < length && this[at + 1] == '*'

    fun String.skipToEndOfLine(at: Int): Int = indexOf('\n', at).takeIf { it >= 0 } ?: length

    fun String.skipBlockComment(at: Int): Int {
        val end = indexOf("*/", startIndex = at + 2)
        return if (end >= 0) end + 2 else length
    }

    fun String.copyStringLiteral(at: Int, out: StringBuilder): Int {
        val quote = this[at]
        out.append(quote)
        var i = at + 1
        while (i < length) {
            val c = this[i]
            out.append(c)
            when {
                c == '\\' && i + 1 < length -> {
                    out.append(this[i + 1])
                    i += 2
                }

                c == quote -> return i + 1

                else -> i++
            }
        }
        return i
    }

    fun String.copyChar(at: Int, out: StringBuilder): Int {
        out.append(this[at])
        return at + 1
    }
}
