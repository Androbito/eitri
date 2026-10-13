# Reading an APK or AAB signature

What the Signing tool's "verify a build" does, what it does not do, and why it is written by hand.

## What it reports

For a chosen APK or AAB:

- which signature schemes are present: v1 (JAR signing), v2, v3, v3.1 and v4
- the certificate behind each one, with its subject, SHA-1 and SHA-256
- whether that certificate matches the key the project signs with, which is the question that matters:
  a build signed with the debug key will be rejected by Play, and one signed with a key Firebase does
  not know will fail sign-in

## What it does not do

It reads the signatures. It does not verify them cryptographically: it does not recompute the digests of
the archive's entries and check them against the signature blocks. So it answers "which key signed
this?" and not "has this file been altered since it was signed?".

That distinction is stated in the UI, not buried here, because the two questions sound alike. For
Servante's purpose the first is the one being asked, and a user who needs the second should run
`apksigner verify`, which is in the Android SDK they already have.

## Why it is hand-written rather than using apksig

`apksig` is Google's own library for this and it does full verification. Servante does not depend on it
yet for one reason: the only version of `com.android.tools.build:apksig` on Maven Central is 2.3.0, from
March 2017, which knows v1 and v2 and nothing after. Pinning it would make Servante report every modern
APK as missing v3, which is worse than not having the check. The current versions are published to
Google's Maven repository only.

The format is documented and stable, which makes reading it by hand reasonable rather than reckless: an
APK Signing Block sits between the archive contents and the central directory, ends with the magic
string `APK Sig Block 42`, and holds length-prefixed ID to value pairs, one per scheme
(`0x7109871a` for v2, `0xf05368c0` for v3, `0x1b93ad61` for v3.1). v1 is a `META-INF` entry ending in
`.RSA`, `.DSA` or `.EC`. v4 is a sibling `.idsig` file.

The certificates inside each block are found by scanning for the DER tag of an X.509 certificate and
letting the JDK's certificate factory decide what is real, rather than walking each scheme's nested
layout. That is deliberate: the layouts differ per version and are what would break when a new scheme
lands, while a certificate is a certificate.

## When to revisit

Add `apksig` and use it for verification as soon as Google's Maven repository is a dependency source for
this project, which it will be when the Android-side tools in M5 need `bundletool` anyway. Keep the
hand-written reader for the scheme inventory, or drop it then and note the decision in an ADR.
