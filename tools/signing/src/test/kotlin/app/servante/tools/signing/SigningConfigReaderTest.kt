package app.servante.tools.signing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - a Kotlin DSL signingConfigs block yields each config with its store file, alias and passwords
 * - a Groovy signingConfigs block yields the same, written the Groovy way
 * - a password written as a literal is reported as a literal, which is what makes it a finding
 * - a password read from the environment is reported as such and is not a finding
 * - a password read from a Gradle property is reported as such
 * - an expression Servante does not evaluate is reported as an expression, never as a value
 * - which build type uses which config is read, in both DSLs
 * - a build file with no signingConfigs yields nothing rather than failing
 */
class SigningConfigReaderTest {
    private val reader = SigningConfigReader()

    private val kotlinDsl = """
        android {
            namespace = "app.fjord.notes"

            signingConfigs {
                create("release") {
                    storeFile = file("../keystores/release.jks")
                    storePassword = "fjord-release-2024"
                    keyAlias = "upload"
                    keyPassword = "fjord-release-2024"
                }
                create("internal") {
                    storeFile = file(providers.gradleProperty("internalStore").get())
                    storePassword = System.getenv("INTERNAL_STORE_PASSWORD")
                    keyAlias = providers.gradleProperty("internalAlias").get()
                }
            }

            buildTypes {
                release {
                    signingConfig = signingConfigs.getByName("release")
                }
                debug {
                    applicationIdSuffix = ".debug"
                }
            }
        }
    """.trimIndent()

    private val groovyDsl = """
        android {
            signingConfigs {
                release {
                    storeFile file("keystore.jks")
                    storePassword System.getenv("KEYSTORE_PASSWORD")
                    keyAlias "release"
                    keyPassword project.property("keyPassword")
                }
            }

            buildTypes {
                release {
                    signingConfig signingConfigs.release
                    minifyEnabled false
                }
            }
        }
    """.trimIndent()

    @Test
    fun `reads the Kotlin DSL configs`() {
        val read = reader.read("app/build.gradle.kts", kotlinDsl)

        assertEquals(listOf("release", "internal"), read.configs.map { it.name })
        val release = read.configs.first()
        assertEquals("../keystores/release.jks", (release.storeFile as ValueSource.Literal).value)
        assertEquals("upload", (release.keyAlias as ValueSource.Literal).value)
        assertEquals("fjord-release-2024", (release.storePassword as ValueSource.Literal).value)
        assertEquals("app/build.gradle.kts", release.location.path)
    }

    @Test
    fun `a password from the environment is reported as such`() {
        val read = reader.read("app/build.gradle.kts", kotlinDsl)
        val internal = read.configs.first { it.name == "internal" }

        assertEquals(
            "INTERNAL_STORE_PASSWORD",
            (internal.storePassword as ValueSource.Environment).variable,
        )
    }

    @Test
    fun `a value read from a Gradle property is reported as a property`() {
        val read = reader.read("app/build.gradle.kts", kotlinDsl)
        val internal = read.configs.first { it.name == "internal" }

        assertEquals("internalAlias", (internal.keyAlias as ValueSource.GradleProperty).name)
    }

    @Test
    fun `a key password that is not declared is absent`() {
        val read = reader.read("app/build.gradle.kts", kotlinDsl)
        val internal = read.configs.first { it.name == "internal" }

        assertEquals(ValueSource.Absent, internal.keyPassword)
    }

    @Test
    fun `reads the Groovy configs`() {
        val read = reader.read("mobile/build.gradle", groovyDsl)

        val release = read.configs.single()
        assertEquals("release", release.name)
        assertEquals("keystore.jks", (release.storeFile as ValueSource.Literal).value)
        assertEquals("release", (release.keyAlias as ValueSource.Literal).value)
        assertEquals("KEYSTORE_PASSWORD", (release.storePassword as ValueSource.Environment).variable)
        assertEquals("keyPassword", (release.keyPassword as ValueSource.GradleProperty).name)
    }

    @Test
    fun `reads which build type uses which config in the Kotlin DSL`() {
        val read = reader.read("app/build.gradle.kts", kotlinDsl)

        assertEquals(mapOf("release" to "release"), read.buildTypeConfigs)
    }

    @Test
    fun `reads which build type uses which config in Groovy`() {
        val read = reader.read("mobile/build.gradle", groovyDsl)

        assertEquals(mapOf("release" to "release"), read.buildTypeConfigs)
    }

    @Test
    fun `a build file with no signing configs yields nothing`() {
        val read = reader.read("core/build.gradle.kts", "android { namespace = \"a\" }")

        assertTrue(read.configs.isEmpty())
        assertTrue(read.buildTypeConfigs.isEmpty())
    }

    @Test
    fun `an expression Servante does not evaluate is reported as an expression`() {
        val read = reader.read(
            "app/build.gradle.kts",
            """
            android {
                signingConfigs {
                    create("release") {
                        storePassword = keystoreProperties["storePassword"] as String
                    }
                }
            }
            """.trimIndent(),
        )

        val source = read.configs.single().storePassword
        assertTrue(source is ValueSource.Expression, "was $source")
        assertTrue("keystoreProperties" in source.text)
    }

    @Test
    fun `a commented out config is not read`() {
        val read = reader.read(
            "app/build.gradle.kts",
            """
            android {
                signingConfigs {
                    // create("old") { keyAlias = "gone" }
                    create("release") { keyAlias = "upload" }
                }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("release"), read.configs.map { it.name })
    }
}
