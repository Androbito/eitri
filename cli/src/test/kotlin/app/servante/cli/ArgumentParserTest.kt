package app.servante.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArgumentParserTest {
    @Test
    fun `a folder is a check`() {
        val parsed = ArgumentParser.parse(listOf("/home/me/app"))

        assertEquals(Arguments.Check("/home/me/app", emptyList()), parsed)
    }

    @Test
    fun `keystores can be added and repeated`() {
        val parsed = ArgumentParser.parse(
            listOf("/home/me/app", "--keystore", "/keys/a.jks", "--keystore", "/keys/b.p12"),
        )

        assertEquals(Arguments.Check("/home/me/app", listOf("/keys/a.jks", "/keys/b.p12")), parsed)
    }

    @Test
    fun `no arguments is not help, it is a missing folder`() {
        val parsed = ArgumentParser.parse(emptyList())

        assertIs<Arguments.Invalid>(parsed)
        assertTrue("No project folder" in parsed.message)
    }

    @Test
    fun `help is asked for explicitly`() {
        assertEquals(Arguments.Help, ArgumentParser.parse(listOf("--help")))
        assertEquals(Arguments.Help, ArgumentParser.parse(listOf("/home/me/app", "-h")))
    }

    @Test
    fun `an unknown option is refused rather than ignored`() {
        val parsed = ArgumentParser.parse(listOf("/home/me/app", "--json"))

        assertIs<Arguments.Invalid>(parsed)
        assertTrue("--json" in parsed.message)
    }

    @Test
    fun `a keystore flag with nothing after it is refused`() {
        val parsed = ArgumentParser.parse(listOf("/home/me/app", "--keystore"))

        assertIs<Arguments.Invalid>(parsed)
        assertTrue("needs the path" in parsed.message)
    }

    @Test
    fun `two folders is refused, because the diagnosis is of one project`() {
        val parsed = ArgumentParser.parse(listOf("/home/me/app", "/home/me/other"))

        assertIs<Arguments.Invalid>(parsed)
        assertTrue("Two project folders" in parsed.message)
    }
}
