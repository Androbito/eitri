package app.servante.tools.signing

import app.servante.core.Availability
import app.servante.core.available
import app.servante.core.unavailable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An OAuth client registered for a package name, with the certificate it is tied to. */
public data class RegisteredClient(
    public val packageName: String,
    public val clientId: String,
    /** Plain lowercase hex SHA-1, which is how `google-services.json` stores it. */
    public val certificateHash: String?,
    /**
     * The OAuth client type Google assigns: 1 is an Android client, 3 a web client. Kept because Google
     * Sign-In needs an Android client for the package, so a fingerprint registered under anything else
     * is not the same thing as one registered under a type 1 client.
     */
    public val clientType: Int?,
)

/** What `google-services.json` registers. */
public data class GoogleServicesConfig(
    public val path: String,
    public val projectId: String?,
    public val clients: List<RegisteredClient>,
) {
    /** Every certificate hash registered for this package, lowercase hex without separators. */
    public fun hashesFor(packageName: String): Set<String> = hashesFor(packageName, clientType = null)

    /**
     * @param clientType when given, only hashes registered under an OAuth client of that type, so
     *   "registered in this Firebase project" and "has an Android OAuth client" stay distinguishable.
     */
    public fun hashesFor(packageName: String, clientType: Int?): Set<String> = clients.asSequence()
        .filter { it.packageName == packageName }
        .filter { clientType == null || it.clientType == clientType }
        .mapNotNull { it.certificateHash?.lowercase() }
        .toSet()

    public fun packageNames(): List<String> = clients.map { it.packageName }.distinct()
}

/**
 * Reads `google-services.json`.
 *
 * The certificate hashes live under each client's `oauth_client[].android_info.certificate_hash`, as
 * plain lowercase SHA-1 hex. The file is read, never written: registering a fingerprint is something the
 * user does in the Firebase console, and Servante's job is to say which one is missing and hand it over
 * in the form that console expects.
 *
 * Unknown keys are ignored on purpose. Google adds fields to this file, and a new one must not stop
 * Servante from answering the question the user asked.
 */
public class GoogleServicesReader {
    private val json = Json { ignoreUnknownKeys = true }

    public fun read(path: String, content: String): Availability<GoogleServicesConfig> {
        val document = runCatching { json.decodeFromString<GoogleServicesDocument>(content) }
            .getOrElse {
                return unavailable("$path could not be read as a google-services.json file.")
            }
        if (document.clients.isEmpty()) {
            return unavailable("$path registers no Android app, so there is nothing to compare against.")
        }
        return GoogleServicesConfig(
            path = path,
            projectId = document.projectInfo?.projectId,
            clients = document.clients.flatMap { client -> client.registeredClients() },
        ).available()
    }
}

@Serializable
private data class GoogleServicesDocument(
    @SerialName("project_info") val projectInfo: ProjectInfo? = null,
    @SerialName("client") val clients: List<Client> = emptyList(),
)

@Serializable
private data class ProjectInfo(@SerialName("project_id") val projectId: String? = null)

@Serializable
private data class Client(
    @SerialName("client_info") val clientInfo: ClientInfo? = null,
    @SerialName("oauth_client") val oauthClients: List<OauthClient> = emptyList(),
) {
    /**
     * One entry per Android OAuth client. A client with none still appears, with no hash, because a
     * package registered in Firebase with no certificate at all is itself worth reporting.
     */
    fun registeredClients(): List<RegisteredClient> {
        val packageName = clientInfo?.androidClientInfo?.packageName ?: return emptyList()
        val android = oauthClients.filter { it.androidInfo != null }
        if (android.isEmpty()) {
            return listOf(
                RegisteredClient(packageName, clientId = "", certificateHash = null, clientType = null),
            )
        }
        return android.map { oauth ->
            RegisteredClient(
                packageName = oauth.androidInfo?.packageName ?: packageName,
                clientId = oauth.clientId.orEmpty(),
                certificateHash = oauth.androidInfo?.certificateHash?.takeIf { it.isNotBlank() },
                clientType = oauth.clientType,
            )
        }
    }
}

@Serializable
private data class ClientInfo(@SerialName("android_client_info") val androidClientInfo: AndroidClientInfo? = null)

@Serializable
private data class AndroidClientInfo(@SerialName("package_name") val packageName: String? = null)

@Serializable
private data class OauthClient(
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_type") val clientType: Int? = null,
    @SerialName("android_info") val androidInfo: AndroidInfo? = null,
)

@Serializable
private data class AndroidInfo(
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("certificate_hash") val certificateHash: String? = null,
)
