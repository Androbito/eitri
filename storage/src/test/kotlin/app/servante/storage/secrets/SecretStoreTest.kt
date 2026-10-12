package app.servante.storage.secrets

import app.servante.core.Availability
import app.servante.core.fakes.FakeProcessRunner
import app.servante.core.process.ProcessOutcome
import app.servante.core.secrets.Secret
import app.servante.core.secrets.SecretKey
import app.servante.core.secrets.SecretStoreKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The command each OS store builds is the thing worth testing: the arguments have to be the ones the
 * tool documents, and the secret has to reach it on standard input rather than on the argument list,
 * where the process table and any captured output would carry it. See `docs/adr/0007`.
 *
 * Behaviours, for each of macOS and Linux:
 * - storing a secret puts nothing secret on the argument list
 * - storing a secret writes it to standard input
 * - reading a secret returns what the tool printed, without the trailing newline
 * - a secret that is not there is unavailable, not an error
 * - a tool that is not installed makes the whole store unavailable, with the reason
 * - the key is namespaced so Servante cannot collide with another app's entries
 */
class MacOsKeychainSecretStoreTest {
    private val runner = FakeProcessRunner()
    private val index = InMemorySecretKeyIndex()
    private val store = MacOsKeychainSecretStore(runner, index)
    private val key = SecretKey(scope = "ai-provider", account = "anthropic")

    @Test
    fun `the store names the macOS keychain`() {
        assertEquals(SecretStoreKind.MacOsKeychain, store.kind)
    }

    @Test
    fun `storing a secret keeps it off the argument list and writes it to standard input`() = runTest {
        store.put(key, Secret("sk-ant-notreal"))

        val command = runner.lastCommand()
        assertFalse("sk-ant-notreal" in command.describe())
        assertTrue("sk-ant-notreal" in runner.secretsWritten.single())
        assertTrue("add-generic-password -U" in runner.secretsWritten.single())
        assertEquals("/usr/bin/security", command.executable)
        assertEquals(listOf("-i"), command.arguments)
    }

    @Test
    fun `the stored item is namespaced to Servante`() = runTest {
        store.put(key, Secret("sk-ant-notreal"))

        assertTrue("servante.ai-provider.anthropic" in runner.secretsWritten.single())
    }

    @Test
    fun `reading a secret returns what the tool printed`() = runTest {
        runner.succeedWith("/usr/bin/security", "sk-ant-notreal\n")

        val read = store.get(key)

        assertEquals("sk-ant-notreal", (read as Availability.Available).value.reveal())
        assertFalse("sk-ant-notreal" in runner.lastCommand().describe())
    }

    @Test
    fun `a secret that is not in the keychain is unavailable`() = runTest {
        runner.failWith("/usr/bin/security", 44, "security: SecItemCopyMatching: not found")

        val read = store.get(key)

        assertTrue(read is Availability.Unavailable)
        assertTrue("anthropic" in read.reason, "the reason should name what was looked for")
    }

    @Test
    fun `a missing security command makes the store unavailable`() = runTest {
        runner.cannotStart("/usr/bin/security")

        val availability = store.availability()

        assertTrue(availability is Availability.Unavailable)
    }

    @Test
    fun `deleting a secret removes it from the index too`() = runTest {
        store.put(key, Secret("sk-ant-notreal"))
        assertEquals(listOf(key), (store.list("ai-provider") as Availability.Available).value)

        store.delete(key)

        assertEquals(emptyList(), (store.list("ai-provider") as Availability.Available).value)
    }
}

class LinuxSecretServiceSecretStoreTest {
    private val runner = FakeProcessRunner()
    private val store = LinuxSecretServiceSecretStore(runner, InMemorySecretKeyIndex())
    private val key = SecretKey(scope = "play-service-account", account = "servante-reader")

    @Test
    fun `storing a secret uses secret-tool and standard input`() = runTest {
        store.put(key, Secret("{ \"type\": \"service_account\" }"))

        val command = runner.lastCommand()
        assertEquals("secret-tool", command.executable)
        assertEquals(
            listOf(
                "store",
                "--label=Servante: play-service-account servante-reader",
                "service",
                "servante.play-service-account.servante-reader",
                "account",
                "servante-reader",
            ),
            command.arguments,
        )
        assertEquals(listOf("{ \"type\": \"service_account\" }"), runner.secretsWritten)
        assertFalse("service_account" in command.describe())
    }

    @Test
    fun `reading a secret strips the trailing newline secret-tool adds`() = runTest {
        runner.succeedWith("secret-tool", "a-json-key\n")

        assertEquals("a-json-key", (store.get(key) as Availability.Available).value.reveal())
    }

    @Test
    fun `a secret that is not stored is unavailable`() = runTest {
        runner.onRun("secret-tool") { ProcessOutcome.Completed(1, "", "No such secret collection") }

        assertTrue(store.get(key) is Availability.Unavailable)
    }

    @Test
    fun `secret-tool not being installed makes the store unavailable with a reason the UI can show`() = runTest {
        runner.cannotStart("secret-tool", "not installed")

        val availability = store.availability()

        assertTrue(availability is Availability.Unavailable)
        assertTrue("secret-tool" in availability.reason)
    }

    @Test
    fun `clearing a secret uses the clear verb`() = runTest {
        store.delete(key)

        assertEquals("clear", runner.lastCommand().arguments.first())
    }
}

class InMemorySecretStoreTest {
    private val store = InMemorySecretStore()
    private val key = SecretKey("ai-provider", "ollama")

    @Test
    fun `it says that secrets last for the session only`() {
        assertEquals(SecretStoreKind.InMemoryForThisSession, store.kind)
    }

    @Test
    fun `a stored secret can be read back`() = runTest {
        store.put(key, Secret("local"))

        assertEquals("local", (store.get(key) as Availability.Available).value.reveal())
    }

    @Test
    fun `a secret that was never stored is unavailable`() = runTest {
        assertTrue(store.get(key) is Availability.Unavailable)
    }

    @Test
    fun `a deleted secret is gone`() = runTest {
        store.put(key, Secret("local"))
        store.delete(key)

        assertTrue(store.get(key) is Availability.Unavailable)
    }

    @Test
    fun `listing returns the keys of one scope only`() = runTest {
        store.put(SecretKey("ai-provider", "ollama"), Secret("a"))
        store.put(SecretKey("ai-provider", "openai"), Secret("b"))
        store.put(SecretKey("play-service-account", "reader"), Secret("c"))

        val keys = (store.list("ai-provider") as Availability.Available).value

        assertEquals(listOf("ollama", "openai"), keys.map { it.account }.sorted())
    }

    @Test
    fun `it is always available, because it needs nothing from the system`() = runTest {
        assertTrue(store.availability() is Availability.Available)
    }
}
