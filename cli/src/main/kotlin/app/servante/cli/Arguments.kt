package app.servante.cli

/** What the command line asked for. */
internal sealed interface Arguments {
    data class Check(val projectPath: String, val keystores: List<String>) : Arguments

    data object Help : Arguments

    /** [message] says which argument was wrong. An unknown option is an error rather than ignored. */
    data class Invalid(val message: String) : Arguments
}

internal object ArgumentParser {
    const val KEYSTORE_FLAG: String = "--keystore"

    fun parse(args: List<String>): Arguments {
        if (args.any { it in HELP_FLAGS }) return Arguments.Help

        val keystores = mutableListOf<String>()
        var projectPath: String? = null
        var invalid: String? = null
        var index = 0
        while (index < args.size && invalid == null) {
            val argument = args[index]
            when {
                argument == KEYSTORE_FLAG -> {
                    val path = args.getOrNull(index + 1)
                    if (path == null) {
                        invalid = "$KEYSTORE_FLAG needs the path of a keystore after it."
                    } else {
                        keystores += path
                    }
                    index += 2
                }

                argument.startsWith("-") -> {
                    invalid = "'$argument' is not an option this command has."
                }

                projectPath != null -> {
                    invalid = "Two project folders were given, '$projectPath' and '$argument'. " +
                        "This command checks one project."
                }

                else -> {
                    projectPath = argument
                    index += 1
                }
            }
        }

        return when {
            invalid != null -> Arguments.Invalid(invalid)
            projectPath != null -> Arguments.Check(projectPath, keystores)
            else -> Arguments.Invalid("No project folder was given.")
        }
    }

    private val HELP_FLAGS = setOf("-h", "--help", "help")
}
