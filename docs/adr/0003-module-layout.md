# 0003. Module layout: core, tools, ui, app

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

Servante is ten independent tools over one shared domain model, built one milestone at a time. A tool that
is not finished must be shippable as hidden rather than broken. `prompts/02-build.md` fixes the module map;
this record says why it is shaped that way and what the dependency rules are. The `mobile-clean-architecture`
skill's feature-module rule applies: features never depend on each other.

## Options considered

1. **A module per tool plus shared `core`, `ui`, `storage`, `app`, `cli`**: Pros: a tool's dependencies are
   visible in its build file, so `tools/signing` cannot accidentally reach the UI; compiling one tool is
   fast; two people can work on two tools without touching the same files; the `cli` module can reuse tool
   logic with no UI on the classpath. Cons: seventeen modules is a lot of build files for a young project.
2. **One module with packages**: Pros: simplest build. Cons: nothing stops a tool importing a Composable or
   another tool; the CLI would drag the whole UI onto its classpath; no compiler-enforced boundary at all.
3. **Three modules (`domain`, `ui`, `app`)**: Pros: a middle ground. Cons: all ten tools share one source
   set, so the boundary that matters most, between tools, is the one that is not enforced.

## Decision

We will use one Gradle module per tool under `tools/`, plus `core`, `storage`, `ui`, `ai`, `app` and `cli`,
with these rules enforced by the build files:

- `tools/*` depend on `core` only, never on `ui`, never on `app`, never on another `tools/*` module.
- `ui` is the design system and holds no business logic; it depends on `core` for the types it renders.
- `app` is the only composition root: it wires tools to screens and owns navigation and DI.
- `cli` depends on `core` and the `tools/*` modules, never on `ui`.
- Platform differences sit behind interfaces in `core` with per-OS implementations.

## Consequences

- Positive: a boundary violation is a compile error, not a review comment.
- Positive: `cli` stays headless by construction, which M9 needs.
- Positive: an unfinished tool is a module behind a feature flag, so `main` always ships.
- Negative: adding a tool means a new module and a `settings.gradle.kts` entry. Convention plugins in
  `build-logic/` keep each module's build file to a few lines.
- Follow-up: `tools/rules` is the shared rule engine both `tools/a11y` and `tools/security` sit on, which
  makes it the one module several tools depend on. It depends on `core` only and holds no tool-specific
  rules, so it stays a `core`-like shared module rather than a tool depending on a tool.
