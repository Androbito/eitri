# Spike: reading the Play app signing certificate

- Date: 2026-10-12
- Roadmap item: M1, "Play App Signing: fetch the app signing certificate fingerprint through the Play
  Developer API **[spike]** confirm which endpoint exposes it for an app without a fresh upload"
- Verdict: **partly possible.** SHA-256 can be read without uploading anything. SHA-1 cannot be read
  from any endpoint that does not change something, and Servante will not change anything to read a
  value.

## How this was established

Google's REST reference pages were not reachable from the environment this was investigated in, so the
answer comes from the generated client library itself, which is the same discovery document the
reference pages are built from: `com.google.apis:google-api-services-androidpublisher:v3-rev20260924-2.0.0`.
Every resource, method and model field below was read out of that artifact. Anyone can repeat it:

```
javap -classpath <jar> 'com.google.api.services.androidpublisher.AndroidPublisher$Appsigning'
javap -classpath <jar> com.google.api.services.androidpublisher.model.GeneratedApksPerSigningKey
```

## What the API exposes

| Resource and method | Kind | Carries a certificate? |
| --- | --- | --- |
| `appsigning.enrollApp` | write | Yes. `EnrollAppResponse.signingCertificate` and `.uploadCertificate`, each a `CertificateHashes` with `certificateHashMd5`, `certificateHashSha1`, `certificateHashSha256` |
| `appsigning.rotateAppSigningKey` | write | Yes, same `CertificateHashes` shape |
| `generatedapks.list(packageName, versionCode)` | **read** | Yes, partly. `generatedApks[].certificateSha256Hash`, one entry per signing key Play has used |
| `generatedapks.download(packageName, versionCode, downloadId)` | read | Indirectly. The generated universal APK is signed by Play, so its certificate is in the file |
| `edits.bundles.upload` | write | No. `Bundle.sha1` and `Bundle.sha256` are hashes of the file that was uploaded, not of a certificate |
| `appsigning` (anything else) | — | There is no get, no list and no describe on this resource. The resource has exactly two methods, both writes |

So `CertificateHashes`, the only model in the whole API with a SHA-1 field, appears solely in the
responses to enrolling an app and to rotating its signing key. Both change the app. Servante reads by
default, and enrolling or rotating a signing key to find out a fingerprint is not a trade anyone would
accept, so those two are out regardless of what they return.

## What Servante will do

1. **SHA-256, read directly.** Call `generatedapks.list` with the package name and the version code of
   a bundle Play has already processed, and take `certificateSha256Hash`. Nothing is uploaded and
   nothing changes. The response is a list because an app that has rotated its key has more than one
   signing key, and every entry is shown rather than only the first.
2. **SHA-1, reported as unavailable.** No read endpoint exposes it. The matrix shows the Play row's
   SHA-1 as unavailable with the reason "the Play Developer API does not expose the SHA-1 of the app
   signing certificate", and points the user at the App signing page of the Play Console, which shows
   both. This is the honest answer and it is what the "Unavailable" pattern in the designs is for.
3. **The full certificate, behind an explicit action.** `generatedapks.download` returns an APK that
   Play signed, and the certificate inside it yields SHA-1 and SHA-256 and the validity dates. It is a
   real download of a real APK, tens of megabytes, so it is never automatic: it is a button that says
   what it will do, and the result is cached against the version code.

## Requirements and failure modes

- Needs at least one bundle uploaded and processed. A brand-new app with no release has no generated
  APKs, and the matrix says so rather than showing a missing certificate.
- Needs a version code. Servante uses the highest version code it can see across the tracks, which
  comes from the Releases tool in M6; until then the user picks one, or Signing reports the Play row as
  unavailable because it does not know which version to ask about.
- The service account needs the Play Console permission to view app information. The empty state in
  Releases explains this.
- A rotated key gives several entries. All are shown: an app that rotated keys has fingerprints that
  both need to be registered in Firebase, and showing one of them would create exactly the bug the tool
  exists to find.

## Consequence for the roadmap

The roadmap item is updated rather than ticked as written: the fingerprint comparison includes the Play
app signing certificate on SHA-256, and states that SHA-1 is not available through the API. The code
path that downloads a generated APK to recover both is listed under M6, where the Play client and the
version code it needs already exist.
