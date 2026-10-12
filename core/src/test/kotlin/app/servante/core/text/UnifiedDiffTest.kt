package app.servante.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - a one-line change produces the minus and plus pair the dialogs show
 * - unchanged lines around the change are kept as context
 * - identical content produces an empty diff
 * - an added line at the end is a plus with no minus
 * - a removed line is a minus with no plus
 * - the header names the file
 */
class UnifiedDiffTest {
    @Test
    fun `a one line change produces one minus and one plus`() {
        val diff = UnifiedDiff.between(
            path = "gradle/libs.versions.toml",
            before = "agp = \"8.14.0\"\nksp = \"2.2.10-2.0.2\"\nkotlin = \"2.2.20\"\n",
            after = "agp = \"8.14.0\"\nksp = \"2.2.20-2.0.3\"\nkotlin = \"2.2.20\"\n",
        )

        assertEquals(
            listOf(
                "--- gradle/libs.versions.toml",
                "+++ gradle/libs.versions.toml",
                " agp = \"8.14.0\"",
                "-ksp = \"2.2.10-2.0.2\"",
                "+ksp = \"2.2.20-2.0.3\"",
                " kotlin = \"2.2.20\"",
            ),
            diff.lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `identical content produces an empty diff`() {
        assertEquals("", UnifiedDiff.between("a.txt", "same\n", "same\n"))
    }

    @Test
    fun `an added line at the end is a plus with no minus`() {
        val diff = UnifiedDiff.between("a.txt", "one\n", "one\ntwo\n")

        assertTrue("+two" in diff)
        assertTrue(diff.lines().none { it.startsWith("-") && !it.startsWith("---") })
    }

    @Test
    fun `a removed line is a minus with no plus`() {
        val diff = UnifiedDiff.between("a.txt", "one\ntwo\n", "one\n")

        assertTrue("-two" in diff)
        assertTrue(diff.lines().none { it.startsWith("+") && !it.startsWith("+++") })
    }

    @Test
    fun `context is limited around a change in a long file`() {
        val before = (1..40).joinToString("\n") { "line $it" }
        val after = before.replace("line 20", "line twenty")

        val diff = UnifiedDiff.between("long.txt", before, after)

        assertTrue("-line 20" in diff)
        assertTrue("+line twenty" in diff)
        val contextLines = diff.lines().count { it.startsWith(" ") }
        assertTrue(
            contextLines <= 6,
            "a change in the middle should not drag the whole file along, had $contextLines context lines",
        )
    }
}
