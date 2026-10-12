package app.servante.storage.secrets

import app.servante.core.fakes.FakeProcessRunner
import app.servante.core.platform.OperatingSystem
import app.servante.core.secrets.SecretStoreKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - each system gets its own store when that store works
 * - a system whose store does not work falls back to memory, and says why
 * - an unknown system falls back to memory, and says why
 * - the fallback reason is a sentence the Settings screen can show as it is
 */
class SecretStoresTest {
    private val runner = FakeProcessRunner()

    @Test
    fun `macOS gets the keychain when security runs`() = runTest {
        val result = SecretStores.forSystem(OperatingSystem.MacOs, runner, InMemorySecretKeyIndex())

        assertEquals(SecretStoreKind.MacOsKeychain, result.store.kind)
        assertNull(result.fallbackReason)
    }

    @Test
    fun `Linux gets the Secret Service when secret-tool runs`() = runTest {
        val result = SecretStores.forSystem(OperatingSystem.Linux, runner, InMemorySecretKeyIndex())

        assertEquals(SecretStoreKind.LinuxSecretService, result.store.kind)
        assertNull(result.fallbackReason)
    }

    @Test
    fun `a Linux desktop without libsecret falls back to memory and says why`() = runTest {
        runner.cannotStart("secret-tool")

        val result = SecretStores.forSystem(OperatingSystem.Linux, runner, InMemorySecretKeyIndex())

        assertEquals(SecretStoreKind.InMemoryForThisSession, result.store.kind)
        assertNotNull(result.fallbackReason)
        assertTrue("libsecret-tools" in result.fallbackReason)
    }

    @Test
    fun `an unknown system falls back to memory and says why`() = runTest {
        val result = SecretStores.forSystem(OperatingSystem.Unknown, runner, InMemorySecretKeyIndex())

        assertEquals(SecretStoreKind.InMemoryForThisSession, result.store.kind)
        assertNotNull(result.fallbackReason)
    }
}
