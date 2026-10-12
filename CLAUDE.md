# Eitri: instructions for agents working in this repository

Eitri is a free, open source desktop toolbox for Android and Kotlin developers (Kotlin, Compose Multiplatform for Desktop). Scope is in `ROADMAP.md`, the build brief is `prompts/02-build.md`, the UI source of truth is `design/`.

## Engineering standards: mobile-craft-skills

All code in this repository follows the skills published at https://github.com/Androbito/mobile-craft-skills. They are declared in `.claude/settings.json`. If they are not loaded in your session, install them before writing code:

```
claude plugin marketplace add Androbito/mobile-craft-skills
claude plugin install mobile-craft-skills@mobile-craft-skills
```

If they cannot be installed, stop and say so. Do not substitute your own conventions.

| Skill | When it applies here |
| --- | --- |
| `mobile-clean-architecture` | Every module and feature. Use its KMP reference: Eitri is Kotlin Multiplatform on the JVM desktop target. Each `tools/*` module is a feature module with domain, data and presentation layers, and screens use unidirectional data flow |
| `tdd-craftsmanship` | All tool logic (parsers, rules, scoring, comparison). Test first, small steps |
| `mobile-testing-strategy` | The test pyramid per module. Where a pattern names an Android-only tool, use its desktop equivalent and record the choice in an ADR |
| `solid-code-review` | Before every pull request, on the diff. Fix the findings or justify them in the PR |
| `mobile-quality-audit` | At the end of each milestone, on Eitri itself: concurrency, performance, security, accessibility, localisation. Its checklists are also the seed for the rule catalogs of Eitri's own Accessibility and Security tools |
| `figma-to-native-ui` | When turning `design/` screens into Compose: tokens and components first, no hard-coded values, stateless screens |
| `api-mock-server` | Mocks of the Play Developer API, OSV.dev and AI providers for tests and demo mode. No test calls a real service |
| `mobile-release-pipeline` | CI with GitHub Actions, trunk-based development, feature flags for unfinished tools. The Fastlane and store upload parts do not apply: Eitri ships desktop installers through GitHub Releases |
| `adr-writer` | Every technical choice worth remembering, in `docs/adr/` |

Where a skill and `prompts/02-build.md` disagree, stop and ask rather than pick one silently.

## Product rules

Read by default, nothing invented, secrets never on disk or in logs, no telemetry, AI opt-in on the user's own key. Details in `prompts/02-build.md`.

## Do not break

`docs/index.html` is the public site served by GitHub Pages.
