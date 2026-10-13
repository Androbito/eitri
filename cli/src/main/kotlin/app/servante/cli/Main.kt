package app.servante.cli

import app.servante.storage.project.FileSystemProjectDetector
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * The Signing tool from a terminal, so M1 can be checked against a real project before the screens
 * exist. See [SigningCheckCommand] for what it is and what it is not.
 */
fun main(args: Array<String>) {
    val exitCode = when (val arguments = ArgumentParser.parse(args.toList())) {
        Arguments.Help -> {
            System.err.println(USAGE)
            SigningCheckCommand.OK
        }

        is Arguments.Invalid -> {
            System.err.println(arguments.message)
            System.err.println()
            System.err.println(USAGE)
            SigningCheckCommand.USAGE
        }

        is Arguments.Check -> check(arguments)
    }
    System.out.flush()
    System.err.flush()
    exitProcess(exitCode)
}

private fun check(arguments: Arguments.Check): Int = runBlocking {
    SigningCheckCommand(
        detector = FileSystemProjectDetector(),
        passwords = ConsolePasswordProvider(System.console(), System.err),
        out = System.out,
        progress = System.err,
    ).run(arguments.projectPath, arguments.keystores)
}

private val USAGE = """
    servante-signing: why does sign-in work in debug and fail in release?

    Usage: servante-signing <project folder> [--keystore <path>]...

    Reads the project, the keystores its signingConfigs name, the debug keystore in ~/.android, and
    google-services.json. Compares every signing identity against what Firebase has registered, then
    prints the diagnosis as Markdown on standard output. Progress and password prompts go to standard
    error, so `servante-signing ~/my-app > diagnosis.md` leaves a clean file to paste into an issue.

    --keystore adds a keystore no build file mentions. Repeat it for several.

    Nothing is written: the project is read and nothing in it is touched. Passwords are asked for at the
    terminal, never taken from an argument or an environment variable, used once and dropped. Whatever
    cannot be read is reported as unknown with the reason, never guessed.

    Exit codes: 0 checked, 2 the arguments were wrong, 3 the folder is not a Gradle project, 4 there was
    nothing to check, 5 the check stopped.
""".trimIndent()
