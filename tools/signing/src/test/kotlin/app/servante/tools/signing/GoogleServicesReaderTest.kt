package app.servante.tools.signing

import app.servante.core.Availability
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the real `google-services.json` in the Fjord Notes fixture, whose hashes are the real SHA-1 of
 * the fixture keystores.
 *
 * Behaviours:
 * - the registered package names are read
 * - the certificate hashes registered for a package are read
 * - a hash registered for a different package does not count for this one
 * - a file that is not JSON is unavailable
 * - a file with no client list is unavailable, because there is nothing to compare against
 * - unknown fields are ignored, so a newer file still reads
 */
class GoogleServicesReaderTest {
    private val fixtures: Path = Path.of(
        requireNotNull(System.getProperty("servante.fixtures")) { "set by the test task" },
    )
    private val reader = GoogleServicesReader()

    private fun fixtureConfig(): GoogleServicesConfig {
        val file = fixtures.resolve("fjord-notes/app/google-services.json")
        val result = reader.read("app/google-services.json", file.readText())
        assertTrue(result is Availability.Available, "expected to read the fixture, got $result")
        return result.value
    }

    @Test
    fun `reads the registered package names`() {
        val config = fixtureConfig()

        assertEquals(listOf("app.fjord.notes", "app.fjord.notes.debug"), config.packageNames())
        assertEquals("fjord-notes", config.projectId)
    }

    @Test
    fun `reads the certificate hashes registered for a package`() {
        val hashes = fixtureConfig().hashesFor("app.fjord.notes")

        assertEquals(
            setOf(
                "2d26154e8daaf5cdf510a87c2a80c0145a21f9a1",
                "8914b81777ba9a35c1ace9bed6ee805e2cd35731",
            ),
            hashes,
        )
    }

    @Test
    fun `a hash registered for another package does not count for this one`() {
        val config = fixtureConfig()

        assertEquals(
            setOf("2d26154e8daaf5cdf510a87c2a80c0145a21f9a1"),
            config.hashesFor("app.fjord.notes.debug"),
        )
    }

    @Test
    fun `a package with no hash at all is still reported`() {
        val result = reader.read(
            "google-services.json",
            """
            {
              "client": [
                {
                  "client_info": { "android_client_info": { "package_name": "app.example" } },
                  "oauth_client": []
                }
              ]
            }
            """.trimIndent(),
        )

        val config = (result as Availability.Available).value
        assertEquals(listOf("app.example"), config.packageNames())
        assertTrue(config.hashesFor("app.example").isEmpty())
    }

    @Test
    fun `unknown fields are ignored`() {
        val result = reader.read(
            "google-services.json",
            """
            {
              "something_new_google_added": { "a": 1 },
              "client": [
                {
                  "client_info": { "android_client_info": { "package_name": "app.example" } },
                  "oauth_client": [
                    {
                      "client_id": "1-x.apps.googleusercontent.com",
                      "client_type": 1,
                      "android_info": {
                        "package_name": "app.example",
                        "certificate_hash": "abc123",
                        "future_field": true
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(setOf("abc123"), (result as Availability.Available).value.hashesFor("app.example"))
    }

    @Test
    fun `a file that is not JSON is unavailable`() {
        val result = reader.read("google-services.json", "not json at all")

        assertTrue(result is Availability.Unavailable)
    }

    @Test
    fun `a file with no clients is unavailable`() {
        val result = reader.read("google-services.json", """{ "project_info": {} }""")

        assertTrue(result is Availability.Unavailable)
        assertTrue("nothing to compare" in result.reason)
    }
}
