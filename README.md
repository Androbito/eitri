# Eitri

The open source toolbox for Android and Kotlin developers. One desktop app for macOS, Windows and Linux that answers the questions Android Studio leaves to a terminal and a forum thread.

**Status: in development. Nothing is released yet.** This repository currently holds the plan, the prompts used to design and build the app, and the project site.

Site and interactive demo: https://androbito.github.io/eitri/

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

## What is in this repository

| Path | What it is |
| --- | --- |
| [`ROADMAP.md`](ROADMAP.md) | The full to-do list, by milestone |
| [`prompts/01-ui-design.md`](prompts/01-ui-design.md) | Prompt for Stitch or Claude Design to generate the UI |
| [`prompts/02-build.md`](prompts/02-build.md) | Prompt for Claude Code to build the app from that UI |
| [`design/`](design/) | Where the exported designs go |
| [`docs/`](docs/) | The project site, served by GitHub Pages |

## How the project gets built

1. Run `prompts/01-ui-design.md` in Stitch or Claude Design and export the result into `design/`.
2. Run `prompts/02-build.md` in Claude Code at the root of this repository. It builds milestone by milestone, starting with the foundations and the Signing tool.
3. Tick the boxes in `ROADMAP.md` as work lands.

## Stack

Kotlin, Compose Multiplatform for Desktop, Gradle Tooling API, Kotlin Analysis API, bundletool, the Google Play Developer API, SQLDelight.

## Contributing

Issues and ideas are welcome. Open an issue before a large change so it can be matched to the roadmap.

## Credits

Inspired by [Sindri](https://getsindri.app) by Walid Sassi, which does this job for iOS and macOS developers. The two projects are separate. Eitri is the name of Sindri's brother in the Norse stories.

Android, Google Play, Firebase and Jetpack Compose are trademarks of Google LLC. Kotlin is a trademark of the Kotlin Foundation. Eitri is an independent project, not affiliated with Google or JetBrains.

## License

[Apache License 2.0](LICENSE)
