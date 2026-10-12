# Servante roadmap

The to-do list for the whole project. Milestones are ordered: each one ships something usable on its own. Checkboxes get ticked as work lands on `main`.

Legend: **[spike]** means the item needs a short investigation before it can be estimated, usually because it depends on what an external API really exposes.

## M0. Foundations

- [x] Install the [mobile-craft-skills](https://github.com/Androbito/mobile-craft-skills) plugin and follow it for all work (see `CLAUDE.md`)
- [x] ADR log in `docs/adr/`, started with the stack and module layout decisions. Ten records, indexed in `docs/adr/README.md`
- [ ] Gradle multi-module project, Kotlin 2.x, Compose Multiplatform for Desktop (JVM target). Gradle 9.8.1, Kotlin 2.4.21, JVM 21 and the convention plugins are in place and building; the Compose Multiplatform target is configured but has never compiled, because Compose pulls androidx from Google's Maven repository and the environment this was built in denies it
- [x] Modules: `core` (domain, no UI), `tools/*` (one per tool), `ai`, `storage`, `ui` (design system), `app` (desktop shell), `cli`. Seventeen modules with the dependency rules enforced by their build files (ADR 0003). `core`, `storage` and `tools/signing` have content; the rest are placeholders behind feature flags
- [ ] Design system in `ui`: color tokens (light and dark), type scale, spacing, status colors, components from the generated designs in `design/`. Tokens, type scale, spacing and the theme are written from `design/tokens.md`, and the three typefaces are bundled; none of it compiles yet for the Compose reason above, and no component is built
- [ ] App shell: sidebar navigation, project picker, Overview screen with one card per tool. Blocked on Compose
- [x] Project detection: point at a folder, find `settings.gradle(.kts)`, modules, application modules, version catalog, `local.properties`. Both DSLs, without executing the build; anything absent or unreadable is reported as unavailable with a reason
- [x] Shared result model: `Finding(severity, title, explanation, location, fix?)` used by every tool, plus evidence and a standard mapping, and the six `ToolState`s the designs draw
- [x] "Unavailable" state everywhere: anything that cannot be read is reported as unavailable, never replaced by a placeholder. `Availability<T>` has exactly two cases and an unavailable one cannot be built without a reason. Carried through the whole of M1; the UI surface for it is blocked on Compose
- [ ] Demo mode: a bundled fictional project and fake accounts so every screen can be shown and recorded without real data. The Fjord Notes fixture and the fakes exist and the tests run on them; the demo-mode wiring needs the app shell
- [x] Secrets store: macOS Keychain, Windows Credential Manager, Linux Secret Service (libsecret), behind one interface, with a session-only fallback that says why (ADR 0007). The Windows implementation is native and is only exercised on a Windows CI runner, which has not run yet
- [x] Audit log (local SQLite): every write Servante performs, with timestamp, what changed and where. Append-only by construction: the schema has no UPDATE and no DELETE
- [x] Confirmation dialog pattern for any write: summary of what will change, then confirm. `WriteGate` is the only write path and `GatedWriter` implements ask, write, record; the dialog that renders a request is blocked on Compose
- [ ] Localization scaffolding, English and French. Both languages are written as one interface per language, so a missing string is a compile error; it has not compiled yet for the Compose reason above
- [ ] Settings screen: appearance, language, SDK path, JDK path, providers, service accounts. The settings themselves, their storage and the privacy registry the screen renders from are done; the screen is blocked on Compose
- [ ] CI: build, unit tests, detekt, ktlint on macOS, Windows and Linux runners. `.github/workflows/pull-request.yml` is written and runs `./gradlew checkAll`, under Xvfb on Linux. It has never run: it needs a pull request
- [ ] Packaging: DMG (Apple silicon and Intel), MSI, DEB and AppImage through GitHub Actions on tag. `.github/workflows/release.yml` and `scripts/packaging/build-appimage.sh` are written; neither has run, and both need the app module to build first
- [ ] Update check against GitHub Releases, opt-in, no telemetry. The setting and the two GitHub hosts are in the network registry, off by default; the check itself is not written

## M1. Signing Doctor (first release, v0.1)

The question it answers: why does Google Sign-In, Maps or Firebase work in debug and fail in release?

- [x] Find keystores: debug keystore, keystores referenced by `signingConfigs`, user-added keystores. A keystore a build file points at that is not on disk is still reported, because that is a finding
- [x] Read a keystore (JKS and PKCS12): aliases, algorithm, key size, validity dates, SHA-1 and SHA-256 fingerprints. Passwords are asked for when needed and never written to disk. The format is detected from the file rather than its extension
- [ ] Parse `signingConfigs` and which build type and flavor uses which config. Build types and configs are read in both DSLs; flavor-level assignment is not, and the Gradle Tooling API this item assumes could not be used because `repo.gradle.org` is denied in this environment and Maven Central carries only a 2021 snapshot. See ADR 0010 and `docs/notes/signing-config-reading.md` for exactly what text reading sees
- [x] Flag secrets committed in Gradle files or in `gradle.properties` inside the repo. Only a literal is flagged: a password read from the environment or a Gradle property is the correct arrangement and is deliberately not reported
- [x] Read `google-services.json`: registered package names and certificate hashes per client, with the OAuth client type kept so a package with no Android client is distinguishable
- [x] Compare: every signing identity against what Firebase has registered. Report which fingerprint is missing for which package. Every cell that is not a plain match carries a reason
- [ ] Play App Signing: fetch the app signing certificate fingerprint through the Play Developer API and include it in the comparison. **Spike done, see [`docs/notes/play-signing-cert.md`](docs/notes/play-signing-cert.md):** SHA-256 is readable with `generatedapks.list` for an app that has a processed bundle, without uploading anything. SHA-1 is exposed only by `appsigning.enrollApp` and `appsigning.rotateAppSigningKey`, which both change the app, so Servante reports the Play SHA-1 as unavailable and points at the Play Console. Recovering both from a downloaded generated APK is moved to M6, where the version code it needs already exists
- [ ] Verify a built APK or AAB: who signed it, which signature schemes (v1 to v4), does it match the expected upload key. Done by reading the APK signing block directly, which gives the schemes and the signer certificates; it does not verify the signatures cryptographically, because the only apksig on Maven Central is from 2017 and knows nothing after v2. See `docs/notes/build-verification.md`
- [x] Certificate validity: warn on short remaining validity and on weak algorithms. Critical once expired, a warning within a year, and healthy stated explicitly so the screen is not empty when nothing is wrong
- [ ] Fingerprint matrix view: rows are identities (debug, upload, Play signing), columns are consumers (Firebase, Google Cloud OAuth clients, Play), cells are match, missing or unknown. The matrix and every cell's state and reason are computed and tested; the view is blocked on Compose
- [x] Copy helpers: fingerprint in the format each console expects. Each fingerprint carries the colon-separated uppercase form, the plain lowercase form `google-services.json` uses, and the abbreviated form for the matrix. The buttons that put them on the clipboard are blocked on Compose
- [x] Export the diagnosis as Markdown, with the matrix, every key, the reasons, and full fingerprints in code spans so they can be pasted

## M2. Compose Analyzer (v0.2)

The question it answers: why does this composable recompose?

- [ ] Run the Compose compiler with reports and metrics enabled on a chosen module, without editing the user's build files (init script)
- [ ] Parse the reports: restartable, skippable, stable and unstable parameters, inferred class stability
- [ ] Explain each unstable parameter: which type, why the compiler treats it as unstable, and what fixes it (immutable collections, `@Immutable`, `@Stable`, stability configuration file, strong skipping)
- [ ] Static rules on source through the Kotlin Analysis API or PSI: unstable lambdas captured in lists, missing `key` in lazy lists, state read too high, `remember` without keys, heavy work in composition, `derivedStateOf` misuse, back-writes
- [ ] Composable graph: hierarchy from a chosen root, state dependencies drawn as edges, recomposition risk per node
- [ ] Score out of 100 across categories (stability, state, lists, side effects, performance, structure), with the formula documented so the number is explainable
- [ ] Each issue shown with its location and a fix as a diff
- [ ] Paste mode: analyze a single composable pasted in, static rules only
- [ ] Deep analysis with AI (see M8), grounded in the compiler data: the model explains and proposes fixes, the graph and line numbers still come from the source
- [ ] Baseline Profile presence check and a reminder when it is missing

## M3. Gradle Doctor (v0.3)

The question it answers: which of my versions do not go together, and why is the build slow?

- [ ] Read versions: Gradle wrapper, AGP, Kotlin, KSP, Compose compiler, JDK toolchain, `compileSdk`, `targetSdk`, `minSdk`
- [ ] Compatibility matrix shipped as a data file and refreshed from the repo: AGP and Gradle, AGP and JDK, Kotlin and KSP, Kotlin and Compose
- [ ] Version catalog: available updates per dependency, stable and pre-release separated, changelog links
- [ ] Apply an update: edit `libs.versions.toml` after confirmation, logged in the audit log
- [ ] `gradle.properties` review: configuration cache, build cache, parallel, JVM args, non-transitive R class, deprecated flags
- [ ] Deprecation scan: `kapt` where KSP exists, `buildSrc` hints, removed AGP APIs, `jcenter()`
- [ ] Duplicate and conflicting dependency versions across modules
- [ ] Build timing: run a profiled build on request and show the slowest tasks and plugins

## M4. SDK and Disk (v0.4)

The question it answers: what is eating 80 GB on this machine?

- [ ] Inventory with sizes: SDK platforms, build-tools, NDKs, CMake, system images, emulators (AVDs), Gradle caches and daemons, wrapper distributions, Kotlin/Native (`.konan`), Android Studio versions and their caches, JDKs
- [ ] Which SDK components the open project needs versus what is installed
- [ ] Unused detection: system images no AVD uses, build-tools no project references, old wrapper distributions
- [ ] Remove to the system trash after confirmation, logged
- [ ] Install a missing component through `sdkmanager`
- [ ] AVD list: start, cold boot, wipe data
- [ ] JDK list and which one Gradle and Studio are using

## M5. Bundle Preflight (v0.5)

The question it answers: will Play accept this build?

- [ ] Open an AAB or APK (bundletool and apksig as libraries)
- [ ] Manifest checks: target API against the current Play requirement, `debuggable`, `allowBackup`, exported components without permission, cleartext traffic
- [ ] Permissions: full merged list, which library brought each one, sensitive permissions that need a Play declaration
- [ ] Native libraries: ABIs present, 16 KB page size alignment per `.so`
- [ ] Size breakdown: dex, resources, assets, native libs, per ABI and per density download size estimate
- [ ] R8: is the build minified, is a mapping file present next to it
- [ ] Version code against what is already on Play (needs M6 credentials)
- [ ] Data safety helper: SDKs detected in the bundle that usually require a declaration
- [ ] Diff two builds: permissions, size, version, SDKs
- [ ] Policy data (required target API, deadlines) shipped as a dated data file, refreshed from the repo

## M6. Releases and Reminders (v0.6)

The questions they answer: where is build 412, and what deadline am I about to miss?

Releases
- [ ] Connect a Google Play service account (JSON key kept in the secrets store)
- [ ] List apps and, per app, each track: internal, closed, open, production
- [ ] Per release: version codes, status, staged rollout percentage, release notes by language
- [ ] Review status **[spike]** document what the API exposes and show "unavailable" for the rest
- [ ] Read only in the first version. Halt, resume and rollout percentage come later, behind confirmation

Reminders
- [ ] Local notifications, no server
- [ ] Play target API deadline for each connected app
- [ ] Upload certificate and keystore validity
- [ ] Service account key age
- [ ] Reminder windows configurable (30, 14, 7, 1 days)
- [ ] History view backed by the audit log

## M7. Accessibility and Security (v0.7)

Two assessments that share the same engine: rules over source, resources, manifest and the built bundle, each producing a score with a documented formula.

Accessibility score. The question it answers: can someone using TalkBack, large text or a switch use this screen?

- [ ] Compose rules: clickable elements without a label or role, images and icons without `contentDescription` (and decorative ones that should be `null`), touch targets under 48 dp, custom gestures without a semantic action, missing `heading` semantics, missing state descriptions on toggles, focus order overrides, `clearAndSetSemantics` that hides content
- [ ] View and XML rules: missing `contentDescription`, `labelFor`, `importantForAccessibility` misuse, touch target size, hard-coded text sizes in `dp` instead of `sp`
- [ ] Text scaling: fixed heights on text containers, `maxLines` without overflow handling, anything that breaks at 200% font scale
- [ ] Contrast: read the theme (Material color scheme, XML colors) and compute contrast ratios for the text and surface pairs actually used, in light and dark
- [ ] Motion and timing: animations that ignore the system "remove animations" setting, auto-dismissing content without a way to extend
- [ ] Localization readiness: hard-coded strings, missing RTL support flag, start and end versus left and right
- [ ] Score out of 100 across categories (labels, touch targets, contrast, text scaling, navigation and focus, motion), per screen and for the whole app
- [ ] Every finding mapped to its WCAG 2.2 success criterion and to the Android accessibility guideline it comes from
- [ ] Fixes as diffs
- [ ] Device scan **[spike]** run the Accessibility Test Framework checks against a running screen on a connected device or emulator and merge the results with the static findings
- [ ] Export the report as Markdown and HTML

Security assessment. The question it answers: what would a pentester flag in the first hour?

- [ ] Manifest and config: exported components without permission, `debuggable`, `allowBackup` and backup rules, cleartext traffic, network security config (user certificates trusted, missing pinning where the project declares it wants it), task affinity and launch mode risks
- [ ] Deep links and intents: unverified app links, intent filters that accept arbitrary data, implicit intents carrying sensitive extras, mutable `PendingIntent`
- [ ] Secrets: API keys, tokens and passwords hard-coded in source, resources, `BuildConfig` and Gradle files, and the same scan on strings inside the built AAB or APK
- [ ] Storage: tokens in plain `SharedPreferences`, world-readable files, sensitive data on external storage, databases without encryption where the project handles credentials
- [ ] Cryptography: ECB mode, static IVs, MD5 and SHA-1 used for security, hard-coded keys, insecure random
- [ ] WebView: JavaScript enabled with untrusted content, `addJavascriptInterface`, file access flags, mixed content
- [ ] Logging: sensitive values written to logcat in release builds
- [ ] Code hardening: R8 off in release, missing Play Integrity where the project declares it, root and tamper checks reported as information only
- [ ] Dependencies: known vulnerabilities for the resolved dependency graph, looked up against OSV.dev (a network call, listed in the privacy registry, and it sends package coordinates only)
- [ ] SQL and injection: raw queries built by string concatenation, unsafe content provider paths
- [ ] Every finding mapped to its OWASP MASVS control and, where one exists, a CWE
- [ ] Result as a grade (A to F) plus counts by severity, with the grading rule documented. A grade is a summary, never a certification, and the UI says so
- [ ] Fixes as diffs where the fix is mechanical, guidance where it is not
- [ ] Baseline file: accept a finding with a reason so it stops being reported, kept in the repo and reviewed in pull requests
- [ ] Export the report as Markdown, HTML and SARIF

## M8. AI Project Review (v0.8)

Bring your own key. Nothing is sent unless the user asks.

- [ ] Providers: Anthropic, OpenAI, Google, plus any OpenAI-compatible endpoint and local models through Ollama
- [ ] Keys live in the OS secrets store, never in files, never in logs
- [ ] Scope picker: the user chooses the modules and files that will be sent, with a preview of the exact payload
- [ ] Automatic exclusion of secrets: keystores, `local.properties`, `google-services.json`, `.env`, anything matched by the secret scanner
- [ ] Token and cost estimate before sending, where the provider publishes prices
- [ ] First-use notice explaining what leaves the machine and to whom
- [ ] Review types: architecture and module boundaries, Compose deep analysis (M2), Gradle and build setup, release readiness, accessibility deep review (M7), security deep review (M7)
- [ ] Strict response contract (JSON schema). Findings must reference real files and lines, anything that does not resolve is dropped
- [ ] Results use the same `Finding` model as local tools, with fixes as diffs. Diffs are never applied automatically
- [ ] Agent log: every request, tool call and response of a review, visible to the user
- [ ] Fully local path: with Ollama, nothing leaves the machine

## M9. CLI and CI (v0.9)

- [ ] `servante doctor` runs Signing, Gradle, Preflight, Accessibility and Security checks headless
- [ ] Output as text, JSON and SARIF
- [ ] Exit codes by severity so it can gate a pipeline
- [ ] GitHub Action wrapper

## M10. Project site and community

- [x] Landing page on GitHub Pages with an interactive demo using fictional data
- [ ] Replace the interactive mock with real screenshots and a recorded demo once M1 ships
- [ ] Contribution guide, issue templates, code of conduct
- [ ] Docs: one page per tool, privacy page listing every network call the app can make
- [ ] Rule authoring guide so contributors can add Compose and Gradle rules

## Later, not committed

- Arabic localization with right-to-left layout
- Android Studio plugin that surfaces Servante findings in the IDE
- Kotlin Multiplatform project support (shared module checks, iOS framework export)
- Device tools: logcat viewer, deep link tester, install a bundle on a device
- Firebase checks through the Firebase Management API (registered fingerprints without relying on the local JSON)
