package app.servante.storage.project

import app.servante.core.Availability
import app.servante.core.project.AndroidProject
import app.servante.core.project.ModuleKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reads the sample projects in `fixtures/` rather than building temporary ones, so the parser meets the
 * same files a user's project has.
 *
 * Behaviours:
 * - a folder with no settings file is unavailable, with a reason naming what was looked for
 * - a folder that does not exist is unavailable
 * - the Kotlin DSL fixture yields its name, its three modules and which one is the application
 * - the Groovy fixture yields its name and modules, and reports no version catalog
 * - a module declared in settings whose directory is missing is reported with a null directory
 * - the version catalog is read, with versions, libraries and plugins
 * - local.properties is read for the SDK path, and its other keys are listed but not their values
 */
class FileSystemProjectDetectorTest {
    private val fixtures: Path = Path.of(
        requireNotNull(System.getProperty("servante.fixtures")) {
            "The servante.fixtures system property is set by the test task in build-logic."
        },
    )
    private val detector = FileSystemProjectDetector()

    private fun detected(name: String): AndroidProject {
        val result = detector.detect(fixtures.resolve(name).toString())
        assertTrue(result is Availability.Available, "expected $name to be detected, got $result")
        return result.value
    }

    @Test
    fun `a folder that is not a Gradle project is unavailable with a reason`() {
        val result = detector.detect(fixtures.resolve("not-a-gradle-project").toString())

        assertTrue(result is Availability.Unavailable)
        assertTrue(
            result.reason.contains("settings.gradle"),
            "the reason should name the file that was looked for, was: ${result.reason}",
        )
    }

    @Test
    fun `a folder that does not exist is unavailable`() {
        val result = detector.detect(fixtures.resolve("no-such-folder").toString())

        assertTrue(result is Availability.Unavailable)
    }

    @Test
    fun `reads the Kotlin DSL fixture`() {
        val project = detected("fjord-notes")

        assertEquals("fjord-notes", project.name)
        assertEquals("settings.gradle.kts", project.settingsFileName)
        assertEquals(listOf(":app", ":core", ":feature:editor"), project.modules.map { it.path })
        assertEquals(listOf(":app"), project.applicationModules.map { it.path })
        assertEquals(ModuleKind.AndroidLibrary, project.modules.first { it.path == ":core" }.kind)
        assertEquals("feature/editor", project.modules.first { it.path == ":feature:editor" }.directory)
    }

    @Test
    fun `reads the Groovy fixture and reports that it has no version catalog`() {
        val project = detected("groovy-app")

        assertEquals("groovy-app", project.name)
        assertEquals("settings.gradle", project.settingsFileName)
        assertEquals(listOf(":mobile", ":shared"), project.modules.map { it.path })
        assertEquals(listOf(":mobile"), project.applicationModules.map { it.path })
        assertTrue(project.versionCatalog is Availability.Unavailable)
        assertTrue(project.localProperties is Availability.Unavailable)
    }

    @Test
    fun `reads the version catalog`() {
        val catalog = (detected("fjord-notes").versionCatalog as Availability.Available).value

        assertEquals("gradle/libs.versions.toml", catalog.path)
        assertEquals("2.2.20", catalog.versions["kotlin"])
        assertEquals("2.2.10-2.0.2", catalog.versions["ksp"])
        assertEquals("com.android.application", catalog.pluginIdForAccessor("libs.plugins.android.application"))
        val okhttp = catalog.libraries.first { it.alias == "okhttp" }
        assertEquals("com.squareup.okhttp3:okhttp", okhttp.module)
        assertEquals("4.12.0", okhttp.version)
        val bom = catalog.libraries.first { it.alias == "compose-bom" }
        assertEquals("composeBom", bom.versionRef)
        assertNull(bom.version)
    }

    @Test
    fun `reads local properties without exposing values other than the SDK path`() {
        val local = (detected("fjord-notes").localProperties as Availability.Available).value

        assertEquals("/Users/aya/Library/Android/sdk", local.sdkDir)
        assertEquals(setOf("sdk.dir"), local.otherKeys)
    }

    @Test
    fun `a module whose directory is missing is reported with no directory`() {
        val root = Files.createTempDirectory("servante-detect")
        root.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "moved"
            include(":app")
            include(":elsewhere")
            """.trimIndent(),
        )
        root.resolve("app").createDirectories()
        root.resolve("app/build.gradle.kts").writeText("""plugins { id("com.android.application") }""")

        val project = (detector.detect(root.toString()) as Availability.Available).value

        assertEquals("app", project.modules.first { it.path == ":app" }.directory)
        val elsewhere = project.modules.first { it.path == ":elsewhere" }
        assertNull(elsewhere.directory)
        assertEquals(ModuleKind.Unknown, elsewhere.kind)
        assertNotNull(project.name)
    }
}
