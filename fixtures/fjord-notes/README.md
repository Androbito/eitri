# Fixture: Fjord Notes

The fictional Android project the designs in `design/` use, `app.fjord.notes`. It is a fixture, not a
buildable Android project: there is no Android SDK here and no Gradle wrapper, because every test reads
these files rather than building them.

Three things in it are deliberately wrong, because tests and demo mode need findings to show:

- `app/build.gradle.kts` holds the release keystore password in plain text, which the secret scanner
  must flag.
- `gradle/libs.versions.toml` pins KSP `2.2.10-2.0.2` against Kotlin `2.2.20`, which do not match.
- `google-services.json` registers the debug and upload certificates but not the Play app signing
  certificate, which is the Signing tool's headline finding.

## The keystores

Real keystores, generated with `keytool`, so the readers are exercised against the formats a user has
rather than against a mock. The passwords are below because they protect nothing: the keys are fictional
and committed on purpose. Never reuse this arrangement for a key that signs something real.

| File | Format | Alias | Store and key password | What it is for |
| --- | --- | --- | --- | --- |
| `keystores/debug.keystore` | JKS | `androiddebugkey` | `android` | The debug key, with the password Android Studio gives every one it generates |
| `keystores/release.jks` | JKS | `upload` | `fjord-release-2024` | The upload key, named by the `release` signing config |
| `keystores/legacy.p12` | PKCS12 | `legacy-key` | `fjord-legacy` | An EC key no build file references, and the one whose validity runs out soonest, in November 2027 |

`legacy.p12` is not reachable from the build files, which is deliberate: it is what the user-added
keystore path and the expiry warning are tested with.
