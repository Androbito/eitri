package app.servante.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - a line comment is removed, a slash inside a string is not
 * - a block comment is removed
 * - an unterminated block comment does not run off the end
 * - the body of a named block is returned, nested braces included
 * - a block that is not there returns null rather than an empty body
 * - a name that appears as part of a longer identifier is not matched
 * - named blocks inside a body are split, in both DSL spellings
 */
class GradleScriptTest {
    @Test
    fun `a line comment is removed and a slash inside a string is kept`() {
        val stripped = GradleScript.stripComments(
            """
            val path = "https://example.com/a" // a comment
            """.trimIndent(),
        )

        assertTrue("https://example.com/a" in stripped)
        assertTrue("a comment" !in stripped)
    }

    @Test
    fun `a block comment is removed`() {
        val stripped = GradleScript.stripComments("a /* gone */ b")

        assertEquals("a  b", stripped)
    }

    @Test
    fun `an unterminated block comment does not run off the end`() {
        assertEquals("a ", GradleScript.stripComments("a /* never closed"))
    }

    @Test
    fun `the body of a named block is returned with nested braces`() {
        val body = GradleScript.blockBody(
            "signingConfigs",
            """
            android {
                signingConfigs {
                    create("release") {
                        keyAlias = "upload"
                    }
                }
            }
            """.trimIndent(),
        )

        assertTrue(body != null)
        assertTrue("""create("release")""" in body)
        assertTrue("""keyAlias = "upload"""" in body)
    }

    @Test
    fun `a block that is not there returns null`() {
        assertNull(GradleScript.blockBody("signingConfigs", "android { namespace = \"a\" }"))
    }

    @Test
    fun `a name inside a longer identifier is not matched`() {
        assertNull(GradleScript.blockBody("signingConfigs", "mySigningConfigs { a = 1 }"))
    }

    @Test
    fun `named blocks are split in the Kotlin DSL spelling`() {
        val blocks = GradleScript.namedBlocks(
            """
            create("release") {
                keyAlias = "upload"
            }
            create("internal") {
                keyAlias = "internal"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("release", "internal"), blocks.map { it.first })
        assertTrue("""keyAlias = "upload"""" in blocks.first().second)
    }

    @Test
    fun `named blocks are split in the Groovy spelling`() {
        val blocks = GradleScript.namedBlocks(
            """
            release {
                keyAlias "release"
            }
            debug {
                applicationIdSuffix ".debug"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("release", "debug"), blocks.map { it.first })
    }
}
