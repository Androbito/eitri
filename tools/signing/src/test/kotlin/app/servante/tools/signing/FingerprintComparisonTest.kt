package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.unavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The headline question of M1: the Play signing certificate is not registered in Firebase, so sign-in
 * works in debug and fails for installs from Play. The matrix has to show exactly that.
 *
 * Behaviours:
 * - a fingerprint registered in google-services.json is a match for Firebase
 * - a fingerprint that is not registered is missing, with a reason naming the package and the file
 * - a package with no Android OAuth client is missing for that column even when Firebase has the hash
 * - the debug key is "not used" for Play rather than mismatched
 * - no google-services.json makes the Firebase columns unknown, carrying the reason
 * - no Play connection makes the Play column unknown, carrying the reason
 * - a keystore Servante could not read is unknown everywhere, with the reason it could not be read
 * - missingPairs lists what the user has to act on
 */
class FingerprintComparisonTest {
    private val debugSha1 = fingerprintOf("2d26154e8daaf5cdf510a87c2a80c0145a21f9a1")
    private val uploadSha1 = fingerprintOf("8914b81777ba9a35c1ace9bed6ee805e2cd35731")
    private val playSha1 = fingerprintOf("f2773db05ac10e946bd8237fe0419ca538d26f0b")

    private fun fingerprintOf(hex: String) = Fingerprint(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    private fun identity(kind: IdentityKind, sha1: Fingerprint?, unreadable: String? = null) = SigningIdentity(
        kind = kind,
        label = kind.name,
        source = "test",
        sha1 = sha1,
        sha256 = null,
        unreadableReason = unreadable,
    )

    private fun firebaseWith(vararg clients: RegisteredClient) = GoogleServicesConfig(
        path = "app/google-services.json",
        projectId = "fjord-notes",
        clients = clients.toList(),
    ).available()

    private fun androidClient(hash: String, packageName: String = "app.fjord.notes") = RegisteredClient(
        packageName = packageName,
        clientId = "1-x.apps.googleusercontent.com",
        certificateHash = hash,
        clientType = 1,
    )

    @Test
    fun `the Fjord Notes case, Play signing missing in Firebase`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(
                identity(IdentityKind.Debug, debugSha1),
                identity(IdentityKind.Upload, uploadSha1),
                identity(IdentityKind.PlayAppSigning, playSha1),
            ),
            firebase = firebaseWith(
                androidClient("2d26154e8daaf5cdf510a87c2a80c0145a21f9a1"),
                androidClient("8914b81777ba9a35c1ace9bed6ee805e2cd35731"),
            ),
            playCertificate = CertificateFingerprints(sha1 = playSha1).available(),
        )

        assertEquals(MatchState.Match, matrix.firstCellOfKind(IdentityKind.Debug, Consumer.Firebase).state)
        assertEquals(MatchState.Match, matrix.firstCellOfKind(IdentityKind.Upload, Consumer.Firebase).state)
        assertEquals(MatchState.Missing, matrix.firstCellOfKind(IdentityKind.PlayAppSigning, Consumer.Firebase).state)
        assertEquals(
            MatchState.Missing,
            matrix.firstCellOfKind(IdentityKind.PlayAppSigning, Consumer.OAuthClient).state,
        )
        assertEquals(MatchState.Match, matrix.firstCellOfKind(IdentityKind.PlayAppSigning, Consumer.Play).state)
        assertEquals(MatchState.NotUsed, matrix.firstCellOfKind(IdentityKind.Debug, Consumer.Play).state)
    }

    @Test
    fun `a missing cell names the package and the file`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(identity(IdentityKind.PlayAppSigning, playSha1)),
            firebase = firebaseWith(androidClient("2d26154e8daaf5cdf510a87c2a80c0145a21f9a1")),
            playCertificate = CertificateFingerprints(sha1 = playSha1).available(),
        )

        val reason = matrix.firstCellOfKind(IdentityKind.PlayAppSigning, Consumer.Firebase).reason

        assertNotNull(reason)
        assertTrue("app.fjord.notes" in reason)
        assertTrue("google-services.json" in reason)
    }

    @Test
    fun `a match carries no reason, because there is nothing to act on`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(identity(IdentityKind.Upload, uploadSha1)),
            firebase = firebaseWith(androidClient("8914b81777ba9a35c1ace9bed6ee805e2cd35731")),
            playCertificate = CertificateFingerprints(sha1 = uploadSha1).available(),
        )

        assertNull(matrix.firstCellOfKind(IdentityKind.Upload, Consumer.Firebase).reason)
    }

    @Test
    fun `a package with only a web OAuth client is missing an Android client`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(identity(IdentityKind.Upload, uploadSha1)),
            firebase = firebaseWith(
                RegisteredClient(
                    packageName = "app.fjord.notes",
                    clientId = "1-web.apps.googleusercontent.com",
                    certificateHash = "8914b81777ba9a35c1ace9bed6ee805e2cd35731",
                    clientType = 3,
                ),
            ),
            playCertificate = unavailable("No Play account is connected."),
        )

        assertEquals(MatchState.Match, matrix.firstCellOfKind(IdentityKind.Upload, Consumer.Firebase).state)
        val oauth = matrix.firstCellOfKind(IdentityKind.Upload, Consumer.OAuthClient)
        assertEquals(MatchState.Missing, oauth.state)
        assertTrue("Google Sign-In" in oauth.reason.orEmpty())
    }

    @Test
    fun `no google-services file makes the Firebase columns unknown with the reason`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(identity(IdentityKind.Upload, uploadSha1)),
            firebase = unavailable("This project has no app/google-services.json."),
            playCertificate = unavailable("No Play account is connected."),
        )

        val cell = matrix.firstCellOfKind(IdentityKind.Upload, Consumer.Firebase)
        assertEquals(MatchState.Unknown, cell.state)
        assertEquals("This project has no app/google-services.json.", cell.reason)
    }

    @Test
    fun `no Play connection makes the Play signing row unknown with the reason`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(identity(IdentityKind.PlayAppSigning, playSha1)),
            firebase = firebaseWith(androidClient("8914b81777ba9a35c1ace9bed6ee805e2cd35731")),
            playCertificate = unavailable("No Play account is connected, so releases cannot be read."),
        )

        val cell = matrix.firstCellOfKind(IdentityKind.PlayAppSigning, Consumer.Play)
        assertEquals(MatchState.Unknown, cell.state)
        assertTrue("No Play account" in cell.reason.orEmpty())
    }

    @Test
    fun `the upload key is never reported as missing from Play`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(identity(IdentityKind.Upload, uploadSha1)),
            firebase = firebaseWith(androidClient("8914b81777ba9a35c1ace9bed6ee805e2cd35731")),
            playCertificate = CertificateFingerprints(sha1 = playSha1).available(),
        )

        // With Play App Signing the upload key is not the key Play signs with. That is the correct
        // arrangement, so the cell is unknown with an explanation and never a mismatch.
        val cell = matrix.firstCellOfKind(IdentityKind.Upload, Consumer.Play)
        assertEquals(MatchState.Unknown, cell.state)
        assertTrue("upload certificate" in cell.reason.orEmpty(), cell.reason.orEmpty())
    }

    @Test
    fun `a keystore that could not be read is unknown everywhere with its reason`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(
                identity(IdentityKind.Upload, sha1 = null, unreadable = "You skipped release.jks."),
            ),
            firebase = firebaseWith(androidClient("8914b81777ba9a35c1ace9bed6ee805e2cd35731")),
            playCertificate = CertificateFingerprints(sha1 = uploadSha1).available(),
        )

        listOf(Consumer.Firebase, Consumer.OAuthClient).forEach { consumer ->
            val cell = matrix.firstCellOfKind(IdentityKind.Upload, consumer)
            assertEquals(MatchState.Unknown, cell.state, "$consumer")
            assertEquals("You skipped release.jks.", cell.reason, "$consumer")
        }
    }

    @Test
    fun `missingPairs lists what the user has to act on`() {
        val matrix = FingerprintComparison.build(
            packageName = "app.fjord.notes",
            identities = listOf(
                identity(IdentityKind.Debug, debugSha1),
                identity(IdentityKind.PlayAppSigning, playSha1),
            ),
            firebase = firebaseWith(androidClient("2d26154e8daaf5cdf510a87c2a80c0145a21f9a1")),
            playCertificate = CertificateFingerprints(sha1 = playSha1).available(),
        )

        val missing = matrix.missingPairs()

        assertEquals(
            listOf(
                IdentityKind.PlayAppSigning to Consumer.Firebase,
                IdentityKind.PlayAppSigning to Consumer.OAuthClient,
            ),
            missing.map { (identity, consumer) -> identity.kind to consumer },
        )
    }
}

/**
 * Two keys of the same kind, which is ordinary: a release signing config plus a keystore the user added,
 * or one keystore holding two aliases. Keying anything by kind makes the second overwrite the first.
 *
 * Behaviours:
 * - each identity keeps its own cells, whichever order they arrive in
 * - a registered key is not reported missing because another key of the same kind is not registered
 * - a key that is not registered is still reported when a registered key of the same kind comes first
 * - the two findings do not share one id
 */
class FingerprintComparisonSameKindTest {
    private val registeredSha1 = fingerprintOf("8914b81777ba9a35c1ace9bed6ee805e2cd35731")
    private val unregisteredSha1 = fingerprintOf("1111111111111111111111111111111111111111")

    private fun fingerprintOf(hex: String) = Fingerprint(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    private fun upload(source: String, sha1: Fingerprint) = SigningIdentity(
        kind = IdentityKind.Upload,
        label = "Upload key",
        source = source,
        sha1 = sha1,
        sha256 = null,
    )

    private val registered = upload("release.jks, alias upload", registeredSha1)
    private val unregistered = upload("extra.jks, alias other", unregisteredSha1)

    private fun matrix(identities: List<SigningIdentity>) = FingerprintComparison.build(
        packageName = "app.fjord.notes",
        identities = identities,
        firebase = GoogleServicesConfig(
            path = "app/google-services.json",
            projectId = "fjord-notes",
            clients = listOf(
                RegisteredClient(
                    packageName = "app.fjord.notes",
                    clientId = "1-x.apps.googleusercontent.com",
                    certificateHash = "8914b81777ba9a35c1ace9bed6ee805e2cd35731",
                    clientType = 1,
                ),
            ),
        ).available(),
        playCertificate = unavailable("No Play account is connected."),
    )

    @Test
    fun `each identity keeps its own cells with the registered key first`() {
        val matrix = matrix(listOf(registered, unregistered))

        assertEquals(MatchState.Match, matrix.cell(registered, Consumer.Firebase).state)
        assertEquals(MatchState.Missing, matrix.cell(unregistered, Consumer.Firebase).state)
    }

    @Test
    fun `each identity keeps its own cells with the unregistered key first`() {
        val matrix = matrix(listOf(unregistered, registered))

        assertEquals(MatchState.Match, matrix.cell(registered, Consumer.Firebase).state)
        assertEquals(MatchState.Missing, matrix.cell(unregistered, Consumer.Firebase).state)
    }

    @Test
    fun `only the key that is not registered is reported missing, in either order`() {
        listOf(listOf(registered, unregistered), listOf(unregistered, registered)).forEach { order ->
            val missing = matrix(order).missingPairs()

            assertEquals(
                listOf(unregistered.source, unregistered.source),
                missing.map { it.first.source },
                "order was ${order.map { it.source }}",
            )
            assertTrue(missing.none { it.first.source == registered.source })
        }
    }

    @Test
    fun `the two keys produce findings with different ids`() {
        val findings = SigningRules.missingRegistrations(matrix(listOf(registered, unregistered)))

        assertEquals(findings.size, findings.map { it.id.value }.distinct().size, "ids: ${findings.map { it.id }}")
    }
}
