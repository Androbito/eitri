package app.servante.tools.signing

import app.servante.core.Availability
import kotlin.time.Instant

/** Where a signing identity came from, which decides how it is labelled and what it is compared to. */
public enum class IdentityKind {
    /** The debug keystore Android Studio generates, or the one the project points at for debug. */
    Debug,

    /** The key the project signs release builds with, which Play calls the upload key. */
    Upload,

    /** The key Play re-signs with, which only Play has. */
    PlayAppSigning,

    /** A keystore Servante found or the user added that is not one of the above. */
    Other,
}

/** Something that can hold a registered fingerprint, which is a column of the matrix. */
public enum class Consumer {
    /** The Firebase project, as `google-services.json` describes it. */
    Firebase,

    /** An Android OAuth client for the package, which Google Sign-In needs. */
    OAuthClient,

    /** Google Play, through the Play Developer API. */
    Play,
}

/**
 * The fingerprints of one certificate, by algorithm, each null when it could not be obtained.
 *
 * Both are kept because the two sides of the comparison do not use the same one: `google-services.json`
 * records SHA-1, and the Play Developer API exposes SHA-256. A comparison is only possible where the two
 * sides overlap, and where they do not, the cell says so instead of showing a mismatch.
 */
public data class CertificateFingerprints(
    public val sha1: Fingerprint? = null,
    public val sha256: Fingerprint? = null,
) {
    public val isEmpty: Boolean get() = sha1 == null && sha256 == null
}

/** One cell. [reason] is filled for anything that is not a plain match, so the UI never shows a bare state. */
public enum class MatchState {
    Match,
    Missing,
    Unknown,
    NotUsed,
}

public data class MatrixCell(public val state: MatchState, public val reason: String? = null)

/**
 * One key Servante knows about.
 *
 * @param sha1 and [sha256] null when the identity is known to exist but its fingerprints could not be
 *   read, a password-protected keystore the user skipped for example. The matrix then shows unknown with
 *   that reason rather than leaving the row out, because a key the user has and Servante cannot read is
 *   something they need to know about.
 */
public data class SigningIdentity(
    public val kind: IdentityKind,
    public val label: String,
    public val source: String,
    public val sha1: Fingerprint?,
    public val sha256: Fingerprint?,
    public val keyAlgorithm: String? = null,
    public val keySizeBits: Int? = null,
    public val validFrom: Instant? = null,
    public val validUntil: Instant? = null,
    public val unreadableReason: String? = null,
) {
    /**
     * What tells two keys apart within one run.
     *
     * The kind alone does not: a project can have a release config and a user-added upload keystore, or
     * one keystore with two aliases, and both are [IdentityKind.Upload]. Keying anything by kind makes
     * the second key overwrite the first, which is how a key that is registered came to be reported as
     * missing. [source] is what the user sees for this key, so it is also what distinguishes it.
     */
    public val id: String get() = "${kind.name.lowercase()}:$source"

    /** The id reduced to something that can sit inside a [app.servante.core.FindingId]. */
    public fun slug(): String = id.lowercase().map { if (it.isLetterOrDigit() || it == '.') it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
}

/**
 * The fingerprint matrix from `design/screens/Signing.dc.html`: rows are the keys the project signs
 * with, columns are the places a fingerprint has to be registered, cells say whether it is.
 *
 * Every cell that is not a match carries a reason. That is the difference between this and a table of
 * ticks: "Missing" alone tells the user nothing they can act on, while "Missing: the Play signing
 * certificate is not registered for app.fjord.notes" tells them exactly what to paste where.
 */
public data class FingerprintMatrix(
    public val packageName: String,
    public val identities: List<SigningIdentity>,
    public val consumers: List<Consumer>,
    private val cells: Map<Pair<String, Consumer>, MatrixCell>,
) {
    /** Keyed by [SigningIdentity.id], not by kind: see the note on that property. */
    public fun cell(identity: SigningIdentity, consumer: Consumer): MatrixCell =
        cells[identity.id to consumer] ?: MatrixCell(MatchState.Unknown, "Not compared.")

    /**
     * The cell for the first identity of this kind, for a caller that has a kind and not an identity.
     *
     * Returns unknown when there is no such identity, and when there is more than one it answers for
     * the first, which is why nothing inside this file uses it.
     */
    public fun firstCellOfKind(kind: IdentityKind, consumer: Consumer): MatrixCell =
        identities.firstOrNull { it.kind == kind }?.let { cell(it, consumer) }
            ?: MatrixCell(MatchState.Unknown, "No $kind key was found.")

    public fun missingPairs(): List<Pair<SigningIdentity, Consumer>> = identities.flatMap { identity ->
        consumers.mapNotNull { consumer ->
            if (cell(identity, consumer).state == MatchState.Missing) identity to consumer else null
        }
    }
}

/**
 * Builds the matrix by comparing what the project signs with against what each consumer has registered.
 *
 * Comparison is on SHA-1, because that is what `google-services.json` stores and what the Play
 * Developer API returns; SHA-256 is carried alongside for the copy helpers, since the Firebase console
 * asks for both.
 */
public object FingerprintComparison {
    public fun build(
        packageName: String,
        identities: List<SigningIdentity>,
        firebase: Availability<GoogleServicesConfig>,
        playCertificate: Availability<CertificateFingerprints>,
    ): FingerprintMatrix {
        val cells = mutableMapOf<Pair<String, Consumer>, MatrixCell>()
        identities.forEach { identity ->
            cells[identity.id to Consumer.Firebase] =
                firebaseCell(packageName, identity, firebase, clientType = null)
            cells[identity.id to Consumer.OAuthClient] =
                firebaseCell(packageName, identity, firebase, clientType = ANDROID_OAUTH_CLIENT_TYPE)
            cells[identity.id to Consumer.Play] = playCell(identity, playCertificate)
        }
        return FingerprintMatrix(
            packageName = packageName,
            identities = identities,
            consumers = Consumer.entries.toList(),
            cells = cells,
        )
    }

    private fun firebaseCell(
        packageName: String,
        identity: SigningIdentity,
        firebase: Availability<GoogleServicesConfig>,
        clientType: Int?,
    ): MatrixCell {
        val config = when (firebase) {
            is Availability.Unavailable -> return MatrixCell(MatchState.Unknown, firebase.reason)
            is Availability.Available -> firebase.value
        }
        val fingerprint = identity.sha1
            ?: return MatrixCell(MatchState.Unknown, identity.noSha1Reason())
        val registered = config.hashesFor(packageName, clientType)
        return if (fingerprint.plainLowercase in registered) {
            MatrixCell(MatchState.Match)
        } else {
            MatrixCell(
                MatchState.Missing,
                when (clientType) {
                    ANDROID_OAUTH_CLIENT_TYPE ->
                        "No Android OAuth client for $packageName carries this fingerprint, " +
                            "so Google Sign-In will reject builds signed with this key."

                    else ->
                        "This fingerprint is not registered for $packageName in ${config.path}."
                },
            )
        }
    }

    /**
     * The Play column.
     *
     * The upload key is deliberately never reported as missing here. With Play App Signing the upload
     * key is not the key Play signs with, by design, so a mismatch is the normal, correct arrangement
     * and reporting it would train people to ignore the tool. Play does hold the upload certificate, but
     * no read endpoint exposes it (see `docs/notes/play-signing-cert.md`), so that cell is unknown with
     * the reason rather than a guess either way.
     */
    private fun playCell(
        identity: SigningIdentity,
        playCertificate: Availability<CertificateFingerprints>,
    ): MatrixCell = when (identity.kind) {
        IdentityKind.Debug ->
            MatrixCell(MatchState.NotUsed, "Debug builds are not distributed through Play.")

        IdentityKind.PlayAppSigning -> when (playCertificate) {
            is Availability.Unavailable -> MatrixCell(MatchState.Unknown, playCertificate.reason)
            is Availability.Available -> MatrixCell(MatchState.Match)
        }

        IdentityKind.Upload, IdentityKind.Other -> MatrixCell(
            MatchState.Unknown,
            "Play holds an upload certificate for this app, but the Play Developer API does not expose " +
                "it, so Servante cannot compare this key against it. The Play Console's App signing " +
                "page shows both certificates.",
        )
    }

    /**
     * Why a key has no SHA-1 to compare. Separate from "could not be read": a Play app signing
     * certificate read through the API has a SHA-256 and no SHA-1, which is a different situation from a
     * keystore nobody gave the password for, and the user needs to be told which one it is.
     */
    private fun SigningIdentity.noSha1Reason(): String = when {
        unreadableReason != null -> unreadableReason

        sha256 != null ->
            "google-services.json records SHA-1 fingerprints, and this certificate is only known by " +
                "its SHA-256, so the two cannot be compared. Check the Play Console's App signing page " +
                "against the fingerprints in your Firebase project settings."

        else -> "This key's fingerprint could not be read."
    }

    private const val ANDROID_OAUTH_CLIENT_TYPE = 1
}
