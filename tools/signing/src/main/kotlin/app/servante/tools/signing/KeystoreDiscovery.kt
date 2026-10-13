package app.servante.tools.signing

import app.servante.core.project.AndroidProject

/** Why Servante is looking at this keystore. */
public enum class KeystoreOrigin {
    /** `~/.android/debug.keystore`, the one Android Studio generates. */
    AndroidStudioDebug,

    /** Named by a `signingConfigs` entry in a module's build file. */
    SigningConfig,

    /** Added by the user in the UI. */
    UserAdded,
}

/**
 * A keystore Servante found and the context it was found in.
 *
 * @param declaredPath what the build file said, kept so a finding can quote it when the file is not
 *   where the build file says it is, which is the usual cause of a release build failing on one machine
 *   and not another.
 */
public data class KeystoreCandidate(
    public val path: String,
    public val origin: KeystoreOrigin,
    public val exists: Boolean,
    public val configName: String? = null,
    public val modulePath: String? = null,
    public val declaredPath: String? = null,
)

/**
 * Finds the keystores a project signs with.
 *
 * Three sources, in the order the roadmap lists them: the debug keystore, the keystores named by
 * `signingConfigs`, and whatever the user added. A keystore named by a build file that is not on disk is
 * still returned, with `exists` false, because "your build file points at a keystore that is not there"
 * is a finding and not a reason to show nothing.
 */
public class KeystoreDiscovery(private val fileSystem: SigningFileSystem) {
    public fun discover(
        project: AndroidProject,
        declarationsByModule: Map<String, SigningDeclarations>,
        userAdded: List<String> = emptyList(),
    ): List<KeystoreCandidate> {
        val candidates = mutableListOf<KeystoreCandidate>()

        val debugKeystore = fileSystem.resolve(fileSystem.homeDirectory(), ANDROID_DEBUG_KEYSTORE)
        candidates += KeystoreCandidate(
            path = debugKeystore,
            origin = KeystoreOrigin.AndroidStudioDebug,
            exists = fileSystem.exists(debugKeystore),
        )

        declarationsByModule.forEach { (modulePath, declarations) ->
            val moduleDirectory = project.modules.firstOrNull { it.path == modulePath }?.directory
            declarations.configs.forEach { config ->
                val declared = (config.storeFile as? ValueSource.Literal)?.value ?: return@forEach
                val base = listOfNotNull(project.rootPath, moduleDirectory).let { parts ->
                    parts.drop(1).fold(parts.first()) { acc, part -> fileSystem.resolve(acc, part) }
                }
                val resolved = fileSystem.resolve(base, declared)
                candidates += KeystoreCandidate(
                    path = resolved,
                    origin = KeystoreOrigin.SigningConfig,
                    exists = fileSystem.exists(resolved),
                    configName = config.name,
                    modulePath = modulePath,
                    declaredPath = declared,
                )
            }
        }

        userAdded.forEach { path ->
            candidates += KeystoreCandidate(
                path = path,
                origin = KeystoreOrigin.UserAdded,
                exists = fileSystem.exists(path),
            )
        }

        return candidates.distinctBy { it.path to it.configName }
    }

    private companion object {
        private const val ANDROID_DEBUG_KEYSTORE = ".android/debug.keystore"
    }
}
