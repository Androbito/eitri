# Servante

The open source toolbox for Android and Kotlin developers. One desktop app for macOS, Windows and Linux that answers the questions Android Studio leaves to a terminal and a forum thread.

**Status: in development. Nothing is released yet.** The foundations and the Signing tool's logic are
built and tested; the user interface is not. See [What works today](#what-works-today).

Site and interactive demo: https://androbito.github.io/servante/

## The tools

| Tool | The question it answers |
| --- | --- |
| Signing | Why does sign-in work in debug and fail in release? |
| Compose | Why does this composable recompose? |
| Gradle | Which of these versions do not go together? |
| SDK and disk | What is taking all this space? |
| Preflight | Will Play accept this build? |
| Releases | Where is build 412? |
| Reminders | What deadline is coming? |
| Accessibility | Can someone using TalkBack or large text use this screen? |
| Security | What would a pentester flag in the first hour? |
| AI review | What would a senior reviewer say about this project? |

AI review is optional and runs on your own API key (Anthropic, OpenAI, Google, any OpenAI-compatible endpoint) or on a local model through Ollama. Every other tool works without it.

## Principles

- It reads by default. Every write is confirmed first and recorded in a local log.
- Nothing is made up. What cannot be read is shown as unavailable.
- No account, no server, no telemetry.
- Free and open source, Apache 2.0, no paid tier.

## What works today

| Part | State |
| --- | --- |
| Project reading | Reads `settings.gradle(.kts)` in both DSLs, the modules and which of them are application modules, the version catalog and `local.properties`, without running Gradle |
| Signing | Reads JKS and PKCS12 keystores, computes SHA-1 and SHA-256, reads `signingConfigs` and `google-services.json`, compares every key against Firebase, the Android OAuth clients and Play, reads the signature schemes of a built APK, and exports the diagnosis as Markdown |
| Local database | Append-only audit log, settings, recent projects, cached results, in SQLite through SQLDelight |
| Secrets | The OS store on each of the three systems, behind one interface, with a session-only fallback that says why |
| Writes | One gate: it asks, writes atomically, and records. There is no other write path |
| User interface | **Not built.** The design tokens and both languages are written; no screen exists yet |
| CLI | Not started |

210 tests. Run them with `./gradlew checkAll`, which is also what CI runs.

## Building it

```
./gradlew checkAll          # tests, ktlint and detekt across every module
./gradlew :core:test        # one module
./gradlew ktlintFormat      # fix formatting
```

JDK 21 is the only prerequisite; the Gradle wrapper fetches the rest. The build resolves dependencies
from Maven Central and from Google's Maven repository, which Compose Multiplatform needs.

Demo mode will run against the fictional project in [`fixtures/fjord-notes`](fixtures/fjord-notes),
which is the `app.fjord.notes` project the designs show. It is not wired up until the app shell exists.

## What is in this repository

| Path | What it is |
| --- | --- |
| [`ROADMAP.md`](ROADMAP.md) | The full to-do list, by milestone |
| [`prompts/01-ui-design.md`](prompts/01-ui-design.md) | Prompt for Stitch or Claude Design to generate the UI |
| [`prompts/02-build.md`](prompts/02-build.md) | Prompt for Claude Code to build the app from that UI |
| [`design/`](design/) | Where the exported designs go |
| [`CLAUDE.md`](CLAUDE.md) | Standing instructions for agents, including the skills to follow |
| [`docs/`](docs/) | The project site, served by GitHub Pages, plus the decision records, notes and audits below |
| [`docs/adr/`](docs/adr/) | Why the stack and the structure are what they are |
| [`docs/notes/`](docs/notes/) | Investigations: what the Play API really exposes, what reading a signature can and cannot tell you |
| [`docs/audits/`](docs/audits/) | The quality audit at the end of each milestone, on Servante itself |
| [`fixtures/`](fixtures/) | Sample projects and test keystores the tests read |
| [`design/GAPS.md`](design/GAPS.md) | Where the designs are silent and what was built instead |

## How the project gets built

1. Run `prompts/01-ui-design.md` in Stitch or Claude Design and export the result into `design/`.
2. Run `prompts/02-build.md` in Claude Code at the root of this repository. It builds milestone by milestone, starting with the foundations and the Signing tool.
3. Tick the boxes in `ROADMAP.md` as work lands.

## Engineering standards

The code follows the practices published as agent skills in [mobile-craft-skills](https://github.com/Androbito/mobile-craft-skills): clean architecture, TDD, a test pyramid, SOLID reviews, quality audits and ADRs. [`CLAUDE.md`](CLAUDE.md) says which skill applies where, and the repository declares the plugin so Claude Code sessions pick it up.

## Stack

Kotlin 2.4, Compose Multiplatform for Desktop, Gradle 9.8, a JVM 21 toolchain, SQLDelight, Ktor,
kotlinx.serialization, and the Google Play Developer API. Later milestones add the Gradle Tooling API,
the Kotlin Analysis API and bundletool. Every version is pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) with the date it was checked against its
publishing repository.

## Contributing

Issues and ideas are welcome. Open an issue before a large change so it can be matched to the roadmap.

## Credits

Inspired by [Sindri](https://getsindri.app) by Walid Sassi, which does this job for iOS and macOS developers. The two projects are separate. A servante is the French word for the rolling tool cabinet in a mechanic's workshop.

Android, Google Play, Firebase and Jetpack Compose are trademarks of Google LLC. Kotlin is a trademark of the Kotlin Foundation. Servante is an independent project, not affiliated with Google or JetBrains.

## License

[Apache License 2.0](LICENSE)
