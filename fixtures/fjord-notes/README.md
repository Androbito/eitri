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
