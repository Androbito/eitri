# Prompt 2: build the project (Claude Code)

Run this in Claude Code at the root of this repository, after the designs from Prompt 1 have been exported into `design/`. It is written to be run milestone by milestone: the last section tells the agent where to stop.

---

You are building **Eitri**, a free and open source desktop toolbox for Android and Kotlin developers, for macOS, Windows and Linux. The license is Apache 2.0. There is no paid tier, no account and no backend.

## Read these first

1. `ROADMAP.md`: the full feature list, by milestone. It is the scope. Tick boxes as you complete items.
2. `design/tokens.md`, `design/components/` and `design/screens/`: the UI designs. They are the source of truth for layout, color, type and component behavior. Look at the images, read any exported HTML for exact values.
3. `prompts/01-ui-design.md`: the brief the designs were made from. Use it when a design is missing a state.

If `design/` is empty, stop and say so. Do not invent the UI.

## Product rules, not negotiable

- **Read by default.** Every write (file edit, delete, install, API mutation) goes through a confirmation that lists exactly what will change, then gets recorded in the audit log.
- **Nothing is invented.** Data that cannot be read is surfaced as `Unavailable(reason)`. No placeholder values, no silent fallbacks, no guessed numbers.
- **Secrets never touch disk or logs.** API keys, service account JSON and keystore passwords live in the OS secrets store or in memory only. Keystore passwords are used once and dropped.
- **No telemetry, no backend.** The only network calls are to Google APIs with the user's credentials, to Maven repositories for version lookups, to GitHub for the update check and data files, and to an AI provider when the user explicitly sends a review. Keep a single registry of allowed destinations in code and render the Settings privacy list from it, so the list cannot drift from reality.
- **AI is opt-in and bring-your-own-key.** Nothing is sent without the user seeing the payload scope, the destination and, when prices are known, a cost estimate.
- **Explainable results.** Every finding carries severity, explanation, location and evidence. Scores have a documented formula.

## Stack

- Kotlin 2.x, JVM 21 toolchain
- Compose Multiplatform for Desktop for the UI
- Coroutines and Flow, kotlinx.serialization, kotlinx-datetime
- Ktor client for HTTP
- SQLDelight (SQLite) for the audit log, settings and cached results
- Koin or manual DI, your choice, keep it light
- Kotlin Analysis API (or PSI through kotlin-compiler-embeddable if the Analysis API standalone mode is not workable) for source analysis
- Gradle Tooling API to talk to the user's builds
- bundletool and apksig as libraries for AAB and APK inspection
- Google Play Developer API (androidpublisher v3) with service account auth
- Secrets: a `SecretStore` interface with macOS Keychain, Windows Credential Manager and Linux Secret Service implementations (java-keyring or JNA). If no store is available on Linux, say so in the UI and keep secrets in memory for the session only
- Tests: kotlin.test, Turbine, Compose UI tests for the main flows, fixture projects under `fixtures/`
- Quality: detekt, ktlint, explicit API mode in `core`

Verify current versions and compatibility of every dependency before pinning it. Do not rely on remembered version numbers.

## Architecture

```
app/        desktop entry point, window, navigation, DI wiring
ui/         design system: tokens, theme (light, dark), components. No business logic
core/       domain model: Project, Finding, Severity, Availability, AuditEvent, Result types
storage/    SQLDelight database, SecretStore, settings
tools/
  signing/  keystores, fingerprints, signingConfigs, google-services.json, build verification
  compose/  compiler reports, static rules, graph, scoring
  gradle/   version reading, compatibility matrix, catalog updates, properties review
  sdk/      SDK, AVD, cache and JDK inventory, cleanup
  preflight/ AAB and APK checks, size, permissions, diff
  releases/ Play Developer API client and models
  reminders/ scheduling, notifications
ai/         provider clients, payload builder, secret exclusion, response contract, agent log
cli/        headless entry point sharing the tools modules
data/       compatibility matrix and Play policy data files, versioned and dated
fixtures/   sample Android projects, keystores made for tests, sample AABs
docs/       the GitHub Pages site (already exists, do not break it)
```

Rules:
- `tools/*` modules depend on `core` only, never on `ui`. Each exposes a small API: inputs in, `Flow` of progress and a result made of `Finding`s plus tool-specific data.
- Each tool has a `Fake` data source used by demo mode and by UI tests. Demo mode uses the fictional app "Fjord Notes" (`app.fjord.notes`) from the designs.
- All process execution (Gradle, sdkmanager, emulator) goes through one `ProcessRunner` with timeouts, cancellation and captured output.
- All file writes go through one `WriteGate` that shows the confirmation and writes the audit event. There is no other way to write.
- Platform differences (paths, trash, notifications, secrets) sit behind interfaces in `core` with per-OS implementations.

## UI implementation

- Translate `design/tokens.md` into `ui` tokens one to one. Name tokens by role (`surface`, `surfaceRaised`, `textMuted`, `statusCritical`), not by color.
- Build the components from `design/components/` first, with a gallery screen (debug builds only) that shows every component in both themes. Then assemble screens.
- Match the designs: spacing, radii, type sizes and states. Where a design is silent, follow the nearest existing pattern and list the gap in `design/GAPS.md` instead of improvising a new pattern.
- Every tool screen implements all states: first run, loading, healthy, findings, unavailable, error.
- Fingerprints, versions, paths and code use the monospace face, are selectable, and have a copy action.
- Full keyboard navigation, visible focus, and content descriptions on interactive elements.
- All strings go through the localization layer, English and French from the start.
- Native-feeling window chrome per OS. Follow the system light or dark setting by default.

## Tool notes

Signing
- Read JKS and PKCS12 with `java.security.KeyStore`. Compute SHA-1 and SHA-256 over the DER certificate.
- Parse `signingConfigs` through the Gradle Tooling API with an init script, so both Groovy and Kotlin DSL work and the user's files are not edited.
- `google-services.json`: certificate hashes live under each client's `oauth_client[].android_info`.
- The Play signing certificate lookup is a spike in the roadmap. Do the spike first, write the result in `docs/notes/play-signing-cert.md`, and show "Unavailable" in the matrix if there is no reliable route.

Compose
- Enable compiler reports and metrics through an init script and a dedicated output directory. Never touch the user's build files.
- The compiler reports are the ground truth for stability and skippability. Static rules add findings the compiler does not report. The AI layer explains and proposes, and every reference it returns is validated against the real source before it is shown.
- Write the scoring formula in `docs/notes/compose-score.md` and link it from the score in the UI.

Gradle
- The compatibility matrix is data in `data/compat.json` with a `lastVerified` date per entry and source links. Build it from the official AGP, Kotlin, KSP and Compose compatibility pages. The app shows the date of the data it is using.

Preflight
- Policy values such as the required target API live in `data/play-policy.json` with dates and source links. Same rule: show the date of the data.
- 16 KB check: read ELF program headers of each `.so` and check `PT_LOAD` alignment, and check zip alignment of uncompressed libraries.

Releases
- Read only in this version. Scope the service account request to what reading needs and explain the required Play Console permission in the empty state.

AI
- One `AiProvider` interface: Anthropic, OpenAI, Google, OpenAI-compatible, Ollama. Streaming, cancellation, and a common error model.
- The payload builder takes the user's file selection, removes anything the secret scanner flags (keystores, `local.properties`, `google-services.json`, `.env`, key-looking strings), and produces the exact preview the user sees. What is previewed is byte for byte what is sent.
- Responses must validate against a JSON schema. Findings whose file or line does not resolve are dropped and counted in the agent log.
- Model names and prices are configuration, fetched or user-entered, never hardcoded as facts.

## Working method

- Work through the roadmap in order: M0, then M1, and so on. One milestone per pull request series, small commits.
- For each milestone: write or extend fixtures first, then the tool logic with unit tests, then the UI against the fake data source, then wire the real data source, then run the app and check the screen against the design images in both themes.
- Take screenshots of each finished screen into `design/implemented/` so design and implementation can be compared side by side.
- CI must stay green on macOS, Windows and Linux. A milestone is done when its roadmap boxes are ticked, tests pass on all three, and demo mode shows the tool with fictional data.
- When something in the roadmap turns out to be impossible or different from what it assumes (an API that does not expose a field, for example), do not work around it silently. Update the roadmap item with what you found and surface the limitation in the UI as unavailable.
- Keep `README.md` current: what works today, how to build, how to run demo mode.

## Where to stop

Start with **M0 and M1 only**. At the end, report: what is ticked in the roadmap, what is in `design/GAPS.md`, the result of the Play signing certificate spike, and how to run the app in demo mode. Wait for review before starting M2.
