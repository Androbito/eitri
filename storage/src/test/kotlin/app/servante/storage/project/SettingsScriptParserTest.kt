package app.servante.storage.project

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviours:
 * - a Kotlin DSL settings file yields the root project name
 * - a Groovy settings file yields the root project name
 * - single-argument includes are found
 * - a multi-line include( ... ) block with several paths is found
 * - Groovy includes without parentheses are found
 * - includeBuild is not a module
 * - a commented-out include is not a module
 * - a file with no includes yields no modules rather than failing
 */
class SettingsScriptParserTest {
    @Test
    fun `reads the root project name from the Kotlin DSL`() {
        val parsed = SettingsScriptParser.parse("""rootProject.name = "fjord-notes"""")

        assertEquals("fjord-notes", parsed.rootProjectName)
    }

    @Test
    fun `reads the root project name from Groovy`() {
        val parsed = SettingsScriptParser.parse("rootProject.name = 'groovy-app'")

        assertEquals("groovy-app", parsed.rootProjectName)
    }

    @Test
    fun `finds includes written one per line`() {
        val parsed = SettingsScriptParser.parse(
            """
            rootProject.name = "fjord-notes"
            include(":app")
            include(":core")
            include(":feature:editor")
            """.trimIndent(),
        )

        assertEquals(listOf(":app", ":core", ":feature:editor"), parsed.modulePaths)
    }

    @Test
    fun `finds includes listed in one multi-line call`() {
        val parsed = SettingsScriptParser.parse(
            """
            include(
                ":core",
                ":storage",
                ":tools:signing",
            )
            """.trimIndent(),
        )

        assertEquals(listOf(":core", ":storage", ":tools:signing"), parsed.modulePaths)
    }

    @Test
    fun `finds Groovy includes without parentheses`() {
        val parsed = SettingsScriptParser.parse(
            """
            include ':mobile'
            include ':shared', ':wear'
            """.trimIndent(),
        )

        assertEquals(listOf(":mobile", ":shared", ":wear"), parsed.modulePaths)
    }

    @Test
    fun `does not treat an included build as a module`() {
        val parsed = SettingsScriptParser.parse(
            """
            pluginManagement {
                includeBuild("build-logic")
            }
            include(":app")
            """.trimIndent(),
        )

        assertEquals(listOf(":app"), parsed.modulePaths)
    }

    @Test
    fun `ignores a commented out include`() {
        val parsed = SettingsScriptParser.parse(
            """
            include(":app")
            // include(":experiment")
            /* include(":old") */
            """.trimIndent(),
        )

        assertEquals(listOf(":app"), parsed.modulePaths)
    }

    @Test
    fun `a settings file with no includes has no modules`() {
        val parsed = SettingsScriptParser.parse("""rootProject.name = "single"""")

        assertEquals(emptyList(), parsed.modulePaths)
        assertEquals("single", parsed.rootProjectName)
    }

    @Test
    fun `a settings file without a root project name reports none`() {
        val parsed = SettingsScriptParser.parse("""include(":app")""")

        assertEquals(null, parsed.rootProjectName)
    }
}
