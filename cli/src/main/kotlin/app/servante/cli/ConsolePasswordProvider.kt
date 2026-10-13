package app.servante.cli

import app.servante.core.secrets.Secret
import app.servante.tools.signing.KeystorePasswordProvider
import java.io.Console
import java.nio.file.Path

/**
 * Asks for a keystore password at the terminal.
 *
 * The password is read with [Console.readPassword], so it is not echoed and does not land in the shell
 * history. The character array it comes back in is wiped once the [Secret] is built; the String inside
 * the Secret cannot be wiped on the JVM, which is why it is never written anywhere and is dropped with
 * the process. There is deliberately no option to pass a password as an argument or an environment
 * variable: both would put it in the process list or in a file.
 *
 * With no terminal attached (output piped, or run through Gradle) there is nothing to ask, so it returns
 * null and the key is reported as unreadable with that reason rather than the check failing.
 */
internal class ConsolePasswordProvider(private val console: Console?, private val progress: Appendable) :
    KeystorePasswordProvider {
    private var warned = false

    override suspend fun passwordFor(keystorePath: String): Secret? {
        val name = fileName(keystorePath)
        if (console == null) {
            if (!warned) {
                progress.appendLine(
                    "No terminal is attached, so no keystore password can be asked for. Run the " +
                        "installed command directly instead of through Gradle, and do not pipe its " +
                        "input.",
                )
                warned = true
            }
            return null
        }
        val typed = console.readPassword(prompt(name))
        if (typed == null) return null
        return try {
            when {
                typed.isNotEmpty() -> Secret(String(typed))
                isAndroidStudioDebug(name) -> Secret(ANDROID_STUDIO_DEBUG_PASSWORD)
                else -> null
            }
        } finally {
            typed.fill('\u0000')
        }
    }

    private fun prompt(name: String): String = if (isAndroidStudioDebug(name)) {
        "Password for $name [$ANDROID_STUDIO_DEBUG_PASSWORD]: "
    } else {
        "Password for $name (empty to skip it): "
    }

    private fun fileName(path: String): String = runCatching { Path.of(path).fileName.toString() }.getOrDefault(path)

    private fun isAndroidStudioDebug(name: String) = name == "debug.keystore"

    private companion object {
        /**
         * The password Android Studio gives every debug keystore it generates, which is documented and
         * identical on every machine. Offered as the default of the prompt, never used without the user
         * accepting it, so a debug keystore with a password of its own still asks.
         */
        private const val ANDROID_STUDIO_DEBUG_PASSWORD = "android"
    }
}
