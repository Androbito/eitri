package app.servante.tools.signing

import app.servante.core.SourceLocation
import app.servante.core.text.GradleScript

/**
 * Where a value in a build file comes from.
 *
 * This distinction is the whole point of reading `signingConfigs` rather than evaluating it. A password
 * written as a literal is in the repository and is a finding; the same password read from the
 * environment or from a Gradle property outside the repository is not. Servante does not evaluate the
 * build, so anything else is reported as an expression it did not resolve, never as a value.
 */
public sealed interface ValueSource {
    public data class Literal(public val value: String) : ValueSource

    public data class Environment(public val variable: String) : ValueSource

    public data class GradleProperty(public val name: String) : ValueSource

    /** Something Servante read but did not evaluate. The text is shown so the user can judge it. */
    public data class Expression(public val text: String) : ValueSource

    /** The property is not declared in this block at all. */
    public data object Absent : ValueSource
}

/** One `signingConfigs` entry, as the build file declares it. */
public data class SigningConfigDeclaration(
    public val name: String,
    public val storeFile: ValueSource,
    public val storePassword: ValueSource,
    public val keyAlias: ValueSource,
    public val keyPassword: ValueSource,
    public val location: SourceLocation,
)

/** What a module's build file says about signing. */
public data class SigningDeclarations(
    public val configs: List<SigningConfigDeclaration>,
    /** Build type to the name of the signing config it uses. */
    public val buildTypeConfigs: Map<String, String>,
)

/**
 * Reads `signingConfigs` and `buildTypes` out of a module's build file, in either DSL, without running
 * Gradle and without touching the file.
 *
 * `prompts/02-build.md` specifies the Gradle Tooling API with an init script for this, which resolves
 * values the build computes and is the better answer. This reader is what Servante uses until that is in
 * place; see `docs/notes/signing-config-reading.md` for what each approach can and cannot see, and what
 * is reported as unresolved in the meantime.
 */
public class SigningConfigReader {
    public fun read(buildFilePath: String, buildScript: String): SigningDeclarations {
        val code = GradleScript.stripComments(buildScript)
        val signingBody = GradleScript.blockBody("signingConfigs", code)
        val configs = signingBody?.let { body ->
            GradleScript.namedBlocks(body).map { (name, configBody) ->
                declarationOf(name, configBody, buildFilePath, buildScript)
            }
        }.orEmpty()
        return SigningDeclarations(
            configs = configs,
            buildTypeConfigs = readBuildTypes(code),
        )
    }

    private fun declarationOf(
        name: String,
        body: String,
        buildFilePath: String,
        originalScript: String,
    ): SigningConfigDeclaration = SigningConfigDeclaration(
        name = name,
        storeFile = valueOf("storeFile", body),
        storePassword = valueOf("storePassword", body),
        keyAlias = valueOf("keyAlias", body),
        keyPassword = valueOf("keyPassword", body),
        location = SourceLocation(
            path = buildFilePath,
            line = lineOfDeclaration(name, originalScript),
        ),
    )

    /**
     * The right-hand side of `property = x` (Kotlin DSL) or `property x` (Groovy), classified.
     */
    private fun valueOf(property: String, body: String): ValueSource {
        val match = Regex("""(?<![\w.])$property\s*(?:=\s*)?(.+)""").find(body)
            ?: return ValueSource.Absent
        val expression = match.groupValues[1].trim().trimEnd(',', ';')
        return classify(expression)
    }

    private fun classify(expression: String): ValueSource = CLASSIFIERS.firstNotNullOfOrNull { (pattern, build) ->
        pattern.matcher(expression)?.let { captured -> build(captured) }
    } ?: ValueSource.Expression(expression)

    /**
     * Build type to signing config name, from `signingConfig = signingConfigs.getByName("x")`,
     * `signingConfig signingConfigs.x`, or `signingConfig = signingConfigs["x"]`.
     */
    private fun readBuildTypes(code: String): Map<String, String> {
        val body = GradleScript.blockBody("buildTypes", code) ?: return emptyMap()
        return GradleScript.namedBlocks(body).mapNotNull { (buildType, buildTypeBody) ->
            SIGNING_CONFIG_REFERENCES.firstNotNullOfOrNull { pattern ->
                pattern.find(buildTypeBody)?.groupValues?.get(1)
            }?.let { configName -> buildType to configName }
        }.toMap()
    }

    /** 1-based line of a config's declaration in the original file, for the finding's location. */
    private fun lineOfDeclaration(name: String, script: String): Int? {
        val patterns = listOf(
            Regex("""create\s*\(\s*['"]${Regex.escape(name)}['"]\s*\)"""),
            Regex("""(?<![\w.])${Regex.escape(name)}\s*\{"""),
        )
        script.lineSequence().forEachIndexed { index, line ->
            if (patterns.any { it.containsMatchIn(line) }) return index + 1
        }
        return null
    }

    /**
     * How a right-hand side is recognised, in order. A whole-expression match comes before a contained
     * one, so `file("x")` is a path and not an unevaluated expression that happens to mention a string.
     */
    private sealed interface Matcher {
        fun matcher(expression: String): String?

        /** The expression is nothing but this pattern. */
        data class Whole(val pattern: Regex) : Matcher {
            override fun matcher(expression: String): String? = pattern.matchEntire(expression)?.groupValues?.get(1)
        }

        /** The pattern appears somewhere in the expression. */
        data class Contained(val pattern: Regex) : Matcher {
            override fun matcher(expression: String): String? = pattern.find(expression)?.groupValues?.get(1)
        }
    }

    private companion object {
        private val CLASSIFIERS: List<Pair<Matcher, (String) -> ValueSource>> = listOf(
            Matcher.Whole(Regex("""['"]([^'"]*)['"]""")) to { ValueSource.Literal(it) },
            Matcher.Whole(Regex("""file\s*\(\s*['"]([^'"]+)['"]\s*\)""")) to { ValueSource.Literal(it) },
            Matcher.Contained(Regex("""System\.getenv\s*\(\s*['"]([^'"]+)['"]""")) to {
                ValueSource.Environment(it)
            },
            Matcher.Contained(
                Regex(
                    """(?:providers\.gradleProperty|project\.property|property|findProperty)""" +
                        """\s*\(\s*['"]([^'"]+)['"]""",
                ),
            ) to { ValueSource.GradleProperty(it) },
        )
        private val SIGNING_CONFIG_REFERENCES = listOf(
            Regex("""signingConfig\s*=?\s*signingConfigs\.getByName\s*\(\s*['"]([^'"]+)['"]"""),
            Regex("""signingConfig\s*=?\s*signingConfigs\s*\[\s*['"]([^'"]+)['"]\s*]"""),
            Regex("""signingConfig\s*=?\s*signingConfigs\.([A-Za-z_][A-Za-z0-9_]*)"""),
        )
    }
}
