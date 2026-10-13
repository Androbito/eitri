# 0006. Run detekt and ktlint as command line tools, not Gradle plugins

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

The roadmap requires detekt and ktlint in CI on three operating systems. Servante pins current versions of
Gradle (9.8.1) and Kotlin (2.4.21) and will keep moving, because the app's own subject is build tooling and
it should run on what it talks about. Static analysis plugins are the dependencies most often left behind by
a Gradle or Kotlin bump: detekt's current stable release embeds a Kotlin 2.0.21 compiler for parsing, and a
plugin that reaches into Gradle internals is the first thing to break on a major Gradle release.

## Options considered

1. **The published `-all` command line jars, run from `JavaExec` tasks**: Pros: each tool runs in its own
   JVM with its own embedded Kotlin, so neither constrains the build's Kotlin or Gradle version; a Gradle
   upgrade cannot break them; the invocation is a few lines we own in `build.gradle.kts`. Cons: no
   incremental analysis and no Gradle-level caching, so every run analyses the whole tree; no per-module
   task wiring for free.
2. **The detekt and ktlint Gradle plugins**: Pros: incremental, cached, per-module tasks, baseline support
   out of the box. Cons: couples the lint step to the Gradle and Kotlin versions of this build, which is
   exactly the coupling that blocks upgrades; the ktlint plugin is a third-party wrapper with its own
   release cadence on top of ktlint's.
3. **An alpha release of detekt 2 with the plugin**: Pros: current Kotlin parser. Cons: an alpha gating CI
   on three runners.

## Decision

We will depend on `ktlint-cli` and `detekt-cli` `-all` jars through dedicated Gradle configurations and run
them from `JavaExec` tasks in the root build, because the project's ability to stay on current Gradle and
Kotlin is worth more than incremental lint on a codebase this size.

## Consequences

- Positive: upgrading Gradle or Kotlin cannot break the lint step.
- Positive: the same two commands run identically in CI and on a contributor's machine, on any OS.
- Negative: both analyse the whole tree each run. On a project this size that is seconds, and the tasks are
  declared not up-to-date rather than pretending to be cacheable.
- Negative: detekt's stable parser is a Kotlin version behind the compiler, so syntax newer than Kotlin
  2.0.21 could fail to parse. If that happens, the fix is to move to detekt 2 rather than to pin Kotlin.
- Follow-up: revisit once detekt 2 is stable; its plugin may be worth the coupling if it tracks Kotlin.
