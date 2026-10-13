package app.servante.ui.text

import app.servante.core.Severity
import app.servante.core.ToolId
import app.servante.core.net.NetworkDestination
import app.servante.core.settings.Language

/**
 * French, shipped from the first release.
 *
 * Technical terms developers use in French as they are, "signing", "keystore", "bundle", are kept
 * rather than translated into words no French Android developer says.
 */
public object FrenchStrings : ServanteStrings {
    override val language: Language = Language.French

    override val appName: String = "Servante"
    override val navigationLabel: String = "Outils"
    override val settings: String = "Réglages"
    override val openProject: String = "Ouvrir un projet"
    override val changeProject: String = "Changer de projet"

    override val welcomeHeadline: String = "Ouvrez un projet et voyez ce qui ne va pas."
    override val welcomeBody: String =
        "Servante lit votre projet Android et vérifie la signature, Compose, Gradle, le SDK, vos " +
            "bundles, les versions sur Play, l'accessibilité et la sécurité. Rien n'est modifié sans " +
            "votre confirmation."
    override val openProjectFolder: String = "Ouvrir un dossier de projet"
    override val exploreWithDemoData: String = "Explorer avec des données de démonstration"
    override val welcomeFooter: String = "Aucun compte. Aucune télémétrie. Open source sous Apache 2.0."
    override val recentProjects: String = "Projets récents"
    override val noRecentProjects: String = "Aucun projet ouvert pour l'instant."

    override val overview: String = "Vue d'ensemble"
    override val needsAttention: String = "À regarder"
    override val everythingElse: String = "Le reste"
    override val checkEverythingAgain: String = "Tout revérifier"
    override val nothingCheckedYet: String = "Rien n'a encore été vérifié pour ce projet."

    override val firstRunTitle: String = "Première analyse"
    override val loading: String = "Analyse en cours"
    override val healthy: String = "Tout va bien"
    override val unavailable: String = "Indisponible"
    override val error: String = "Erreur"
    override val nothingWasChanged: String = "Rien n'a été modifié."
    override val tryAgain: String = "Réessayer"
    override val openFullOutput: String = "Afficher la sortie complète"
    override val checkNow: String = "Vérifier"
    override val checkAgain: String = "Revérifier"

    override val cancel: String = "Annuler"
    override val copy: String = "Copier"
    override val copied: String = "Copié"
    override val openInEditor: String = "Ouvrir dans l'éditeur"
    override val copyDiff: String = "Copier le diff"
    override val exportAsMarkdown: String = "Exporter le diagnostic en Markdown"
    override val findings: String = "Constats"

    override val general: String = "Général"
    override val appearance: String = "Apparence"
    override val appearanceFollowSystem: String = "Suivre le système"
    override val appearanceLight: String = "Clair"
    override val appearanceDark: String = "Sombre"
    override val languageLabel: String = "Langue"
    override val checkForUpdatesAtLaunch: String =
        "Chercher une nouvelle version sur GitHub au démarrage"
    override val paths: String = "Chemins"
    override val androidSdkPath: String = "SDK Android"
    override val gradleJdkPath: String = "JDK utilisé par Gradle"
    override val googlePlayAccounts: String = "Comptes Google Play"
    override val connectServiceAccount: String = "Connecter un compte de service"
    override val remove: String = "Retirer"
    override val aiProviders: String = "Fournisseurs d'IA"
    override val notSet: String = "non renseigné"
    override val secretsStoredInKeychain: String =
        "Conservé dans le gestionnaire de secrets du système. Jamais écrit dans un fichier ni dans un " +
            "journal."
    override val privacyTitle: String = "Confidentialité : tout ce que cette application peut contacter"
    override val privacyBody: String =
        "Cette liste est générée depuis le code. Il n'y a aucun autre accès réseau, aucun compte et " +
            "aucune télémétrie."
    override val privacyDestination: String = "Destination"
    override val privacyWhy: String = "Pourquoi"
    override val privacyWhen: String = "Quand"
    override val sourceOnGitHub: String = "Code source sur GitHub"
    override val openSourceUnderApache: String = "Open source sous Apache 2.0."

    override val changeWillBeRecorded: String = "Cette modification sera enregistrée dans l'historique."
    override val nothingIsCommittedForYou: String = "Rien n'est commité à votre place."

    override val signingQuestion: String =
        "Pourquoi la connexion marche en debug et échoue en release ?"
    override val verifyABuild: String = "Vérifier un build"
    override val fingerprintMatrix: String = "Matrice des empreintes"
    override val signingIdentity: String = "Identité de signature"
    override val selectedIdentity: String = "Identité sélectionnée"
    override val source: String = "Source"
    override val algorithm: String = "Algorithme"
    override val validFrom: String = "Valide depuis"
    override val validUntil: String = "Valide jusqu'au"
    override val matchStateMatch: String = "Correspond"
    override val matchStateMissing: String = "Absente"
    override val matchStateUnknown: String = "Inconnue"
    override val matchStateNotUsed: String = "Non utilisée"
    override val keystorePasswordTitle: String = "Mot de passe d'un keystore"
    override val keystorePasswordBody: String =
        "Servante en a besoin pour lire les certificats de ce keystore. Il est utilisé une seule fois, " +
            "gardé en mémoire uniquement, et jamais enregistré."
    override val keystorePassword: String = "Mot de passe du keystore"
    override val alias: String = "Alias"
    override val skipThisKeystore: String = "Ignorer ce keystore"
    override val readKeystore: String = "Lire le keystore"
    override val copyFingerprints: String = "Copier les empreintes"

    override fun healthyWithChecks(checksPassed: Int): String = when (checksPassed) {
        0 -> "Rien à signaler."
        1 -> "Rien à signaler. 1 vérification passée."
        else -> "Rien à signaler. $checksPassed vérifications passées."
    }

    override fun loadingStep(message: String, step: Int, totalSteps: Int): String =
        "$message, étape $step sur $totalSteps."

    override fun projectSummary(projectName: String, toolsNeedingAttention: Int): String =
        when (toolsNeedingAttention) {
            0 -> "$projectName, rien à regarder"
            1 -> "$projectName, 1 outil à regarder"
            else -> "$projectName, $toolsNeedingAttention outils à regarder"
        }

    override fun checkedAgo(relativeTime: String): String = "Vérifié $relativeTime"

    override fun toolName(tool: ToolId): String = when (tool) {
        ToolId.Signing -> "Signature"
        ToolId.Compose -> "Compose"
        ToolId.Gradle -> "Gradle"
        ToolId.SdkDisk -> "SDK et disque"
        ToolId.Preflight -> "Préflight"
        ToolId.Releases -> "Versions"
        ToolId.Reminders -> "Rappels"
        ToolId.Accessibility -> "Accessibilité"
        ToolId.Security -> "Sécurité"
        ToolId.AiReview -> "Revue IA"
    }

    override fun severityName(severity: Severity): String = when (severity) {
        Severity.Critical -> "Critique"
        Severity.Warning -> "Avertissement"
        Severity.Info -> "Information"
        Severity.Healthy -> "Correct"
    }

    override fun privacyReason(destination: NetworkDestination): String = when (destination.reasonKey) {
        "privacy.reason.play" ->
            "Versions sur Play et certificat de signature, avec votre compte de service"

        "privacy.reason.googleAuth" -> "Authentification du compte de service que vous avez connecté"

        "privacy.reason.maven" -> "Dernières versions de vos dépendances"

        "privacy.reason.osv" ->
            "Vulnérabilités connues. N'envoie que les noms et versions des paquets"

        "privacy.reason.github" -> "Nouvelles versions et données de compatibilité à jour"

        "privacy.reason.ai" -> "Les fichiers que vous sélectionnez pour une revue, avec votre clé"

        else -> destination.reasonKey
    }

    override fun privacyWhen(destination: NetworkDestination): String = when (destination.timingKey) {
        "privacy.when.play" -> "Quand vous ouvrez Versions ou Signature"
        "privacy.when.maven" -> "Quand vous ouvrez les mises à jour Gradle"
        "privacy.when.osv" -> "Quand vous lancez une analyse des dépendances"
        "privacy.when.github" -> "Au démarrage, seulement si vous l'activez"
        "privacy.when.ai" -> "Quand vous appuyez sur Envoyer"
        else -> destination.timingKey
    }

    override fun destinationLabel(destination: NetworkDestination): String = when (destination) {
        is NetworkDestination.Host -> destination.host
        is NetworkDestination.UserProvided -> "Votre fournisseur d'IA"
    }
}
