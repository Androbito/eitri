package app.servante.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * This registry is the whole answer to "what can this app contact". The Settings privacy table renders
 * from it, and the HTTP client refuses any host that is not in it, so the table cannot drift from what
 * the app really does.
 *
 * Behaviours:
 * - every destination the roadmap allows is registered
 * - nothing else is allowed
 * - a subdomain of an allowed host is allowed; a host that merely ends with one is not
 * - every destination says which tool reaches it and whether the user has to turn it on
 * - no destination is registered twice
 */
class NetworkDestinationsTest {
    @Test
    fun `the registry holds exactly the destinations the product allows`() {
        assertEquals(
            listOf(
                "androidpublisher.googleapis.com",
                "oauth2.googleapis.com",
                "maven.google.com",
                "repo.maven.apache.org",
                "api.osv.dev",
                "github.com",
                "api.github.com",
            ),
            NetworkDestinations.all.filterIsInstance<NetworkDestination.Host>().map { it.host },
        )
    }

    @Test
    fun `the AI provider is a destination the user names, not a fixed host`() {
        val provider = NetworkDestinations.all.filterIsInstance<NetworkDestination.UserProvided>()

        assertEquals(1, provider.size)
        assertTrue(provider.single().requiresOptIn)
    }

    @Test
    fun `an allowed host is allowed`() {
        assertTrue(NetworkDestinations.isAllowed("api.osv.dev"))
        assertTrue(NetworkDestinations.isAllowed("API.OSV.DEV"))
    }

    @Test
    fun `a subdomain of an allowed host is allowed`() {
        assertTrue(NetworkDestinations.isAllowed("uploads.github.com"))
    }

    @Test
    fun `a host that only ends with an allowed host is not allowed`() {
        assertFalse(NetworkDestinations.isAllowed("evilgithub.com"))
        assertFalse(NetworkDestinations.isAllowed("github.com.example.net"))
    }

    @Test
    fun `an unrelated host is not allowed`() {
        assertFalse(NetworkDestinations.isAllowed("telemetry.example.com"))
        assertFalse(NetworkDestinations.isAllowed(""))
    }

    @Test
    fun `every destination says which tool reaches it and whether it is opt in`() {
        NetworkDestinations.all.forEach { destination ->
            assertNotNull(destination.reasonKey, "${destination.label} has no reason")
            assertNotNull(destination.timingKey, "${destination.label} has no timing")
            assertTrue(destination.reasonKey.isNotBlank())
        }
    }

    @Test
    fun `no destination is registered twice`() {
        val labels = NetworkDestinations.all.map { it.label }

        assertEquals(labels.size, labels.distinct().size)
    }
}
