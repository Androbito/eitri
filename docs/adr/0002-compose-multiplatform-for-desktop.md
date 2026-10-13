# 0002. Compose Multiplatform for Desktop as the UI toolkit

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

Servante is a desktop app for macOS, Windows and Linux whose whole subject matter is Android and Kotlin
tooling: Gradle, the Kotlin compiler, keystores, bundles. The analysis code has to run in a JVM to use
`java.security.KeyStore`, the Gradle Tooling API, bundletool and apksig as libraries. The UI designs in
`design/` are a dense, data-heavy desktop layout: tables, inspectors, diffs, a 232 px sidebar.

## Options considered

1. **Compose Multiplatform for Desktop (JVM target)**: Pros: one language for UI and analysis, so the
   `Finding` model crosses no boundary; the whole JVM ecosystem Servante needs is directly available;
   declarative UI matches the state-per-screen model in `mobile-clean-architecture`; `jpackage` support
   produces DMG, MSI and DEB. Cons: non-native widgets, so window chrome and menus need per-OS work;
   smaller component library than a mature desktop toolkit.
2. **Electron or Tauri with a web UI**: Pros: the designs are already HTML, so the UI would port almost
   directly. Cons: the analysis layer still has to be a JVM process, so the app becomes two runtimes with
   an IPC boundary between them; a second language and build system; much larger installers.
3. **JavaFX or Swing**: Pros: genuinely mature on the desktop, native file dialogs. Cons: imperative UI
   against a design system built on tokens and state variants; no code sharing with the Kotlin idioms the
   rest of the project uses; a shrinking contributor pool among Android developers, who are the people
   most likely to contribute here.

## Decision

We will build the UI with Compose Multiplatform for Desktop on the JVM target, because the analysis code
must be JVM anyway and keeping one language and one process removes an entire integration boundary.

## Consequences

- Positive: `core` types are used unchanged by the UI; no serialization layer between analysis and screens.
- Positive: contributors are Android developers, who already know Compose.
- Negative: window chrome, menus and the file picker need per-OS handling behind interfaces in `core`.
  Windows and Linux chrome is not drawn in `design/` yet and is recorded in `design/GAPS.md`.
- Negative: screenshot testing on the desktop target has fewer ready-made tools than on Android. See
  ADR 0008.
- Follow-up: revisit if Compose Multiplatform's desktop target stops being maintained, or if a tool needs
  a UI component the toolkit cannot express.
