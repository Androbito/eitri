package app.servante.storage.process

import java.io.File

/**
 * Small programs the process runner tests start.
 *
 * They are run as separate JVMs using the JVM the tests run on, so the tests work the same on macOS,
 * Windows and Linux. Shell builtins would not: `echo` is not an executable on Windows and `sleep` is
 * not there at all.
 */
internal object ProcessTestPrograms {
    /** The `java` binary of the JVM running the tests. */
    val javaExecutable: String =
        File(System.getProperty("java.home"), "bin/java").path.let { path ->
            if (File(path).exists()) path else File(System.getProperty("java.home"), "bin/java.exe").path
        }

    /** The class path the tests were started with, which holds the programs below. */
    val classPath: String = System.getProperty("java.class.path")

    fun arguments(mainClass: String, vararg programArguments: String): List<String> =
        listOf("-cp", classPath, mainClass) + programArguments
}

/** Prints its arguments to standard output and a fixed line to standard error, then exits 0. */
internal object PrintsOutput {
    @JvmStatic
    fun main(arguments: Array<String>) {
        arguments.forEach { println(it) }
        System.err.println("on stderr")
    }
}

/** Exits with the code given as its first argument. */
internal object ExitsWithCode {
    @JvmStatic
    fun main(arguments: Array<String>) {
        kotlin.system.exitProcess(arguments.first().toInt())
    }
}

/** Reads everything on standard input and prints it back, so a secret on stdin can be checked. */
internal object EchoesStandardInput {
    @JvmStatic
    fun main(arguments: Array<String>) {
        print(System.`in`.readBytes().decodeToString())
    }
}

/** Never finishes, so the timeout can be tested. */
internal object RunsForever {
    @JvmStatic
    fun main(arguments: Array<String>) {
        while (true) {
            Thread.sleep(SLEEP_MILLIS)
        }
    }

    private const val SLEEP_MILLIS = 60_000L
}

/** Prints three lines with a pause between them, so streaming can be observed. */
internal object PrintsThreeLinesSlowly {
    @JvmStatic
    fun main(arguments: Array<String>) {
        repeat(3) { index ->
            println("line $index")
            System.out.flush()
            Thread.sleep(PAUSE_MILLIS)
        }
    }

    private const val PAUSE_MILLIS = 50L
}
