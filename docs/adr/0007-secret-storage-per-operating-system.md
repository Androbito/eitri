# 0007. Secrets in the OS store, behind one interface, with an in-memory fallback

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

Servante handles three kinds of secret: AI provider API keys, Google Play service account JSON, and
keystore passwords. The product rule is absolute: secrets never touch disk or logs. Keystore passwords are
used once and dropped. The app has no server, so there is nowhere else to put a key: it is the OS store or
nothing. The three target systems expose different mechanisms, and a Linux desktop may have no Secret
Service running at all.

## Options considered

1. **One `SecretStore` interface in `core`, per-OS implementations, in-memory fallback**: Pros: the product
   rule is expressed in one type that every caller goes through; a missing store is a state the UI can
   report rather than a crash; tests use an in-memory implementation with no OS involvement. Cons: three
   implementations to maintain and only one of them is testable on any given CI runner.
2. **An encrypted file next to the database, with a key derived from a passphrase**: Pros: identical on all
   three systems, no native code. Cons: it puts secrets on disk, which the product rules forbid outright,
   and it asks the user to invent and remember another passphrase.
3. **`java-keyring` as a single cross-platform library**: Pros: one dependency, one API. Cons: its Linux
   backend targets gnome-keyring specifically, its last release is old relative to the systems Servante
   targets, and a bug in it is a bug we cannot route around without replacing the whole layer.

## Decision

We will define `SecretStore` in `core` and implement it per system: the `security` command on macOS,
Windows Credential Manager through a JNA mapping of `CredReadW`, `CredWriteW` and `CredDeleteW`, and
`secret-tool` (libsecret) on Linux. When no store is available, `SecretStore.availability()` reports
`Unavailable` with the reason, the UI says so, and secrets are held in memory for the session only.

## Consequences

- Positive: one type to audit for the "never on disk, never in logs" rule.
- Positive: the macOS and Linux implementations shell out through the shared `ProcessRunner`, so their
  command construction and output parsing are unit-testable on any machine with a fake runner, and their
  output is covered by the same no-secrets-in-logs rule as every other process Servante runs.
- Negative: the Windows implementation is native and cannot be exercised on a Linux or macOS runner, so CI
  must run the tests on a Windows runner for it to be verified at all.
- Negative: a Linux user without libsecret re-enters credentials every session. That is visible in the UI
  rather than silent.
- Follow-up: never log a secret value, including in a process error message. The `ProcessRunner` contract
  requires callers to pass secrets on stdin rather than as arguments, because arguments are visible to
  other processes and end up in captured output.
