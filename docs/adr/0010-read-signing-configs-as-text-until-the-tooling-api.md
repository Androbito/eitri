# 0010. Read signingConfigs as text until the Gradle Tooling API is available

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

`prompts/02-build.md` is specific: "Parse `signingConfigs` through the Gradle Tooling API with an init
script, so both Groovy and Kotlin DSL work and the user's files are not edited." That is the right
answer, because the Tooling API gives the configured model after the build script has run, so a store
file assembled from a property, a flavor-specific override or a config applied by a convention plugin all
resolve to real values.

The Tooling API is distributed from `repo.gradle.org`. Maven Central carries only a 7.3 snapshot from
2021 under `org.gradle:gradle-tooling-api`. The environment M1 was built in denies `repo.gradle.org`, so
the dependency cannot be resolved there at all. M1's signing work is otherwise complete and useful, and
blocking all of it on one dependency would have shipped nothing.

## Options considered

1. **Read the build files as text now, keep the interface, add the Tooling API implementation when the
   dependency is reachable**: Pros: M1 ships; the common case, a literal `storeFile` and a literal alias,
   is the great majority of real projects and is read correctly; the limits are visible to the user
   rather than hidden, because `ValueSource.Expression` says "Servante read this and did not evaluate
   it". Cons: a project that computes its signing config is reported as unresolved where the Tooling API
   would have resolved it; two implementations to maintain for a while.
2. **Block M1 until the dependency is reachable**: Pros: one implementation, the right one. Cons: the
   whole milestone waits on an environment setting, and the keystore reading, the comparison, the rules
   and the export do not depend on it at all.
3. **Run Gradle through the command line and parse its output**: Pros: no new dependency; resolves the
   configured model. Cons: configures the user's build on every check, which costs minutes and fails for
   reasons unrelated to signing; parsing console output is more brittle than either alternative, not
   less.
4. **Ask the user to paste their signing config**: Pros: trivial. Cons: the tool exists so that nobody
   has to do that.

## Decision

We will read `signingConfigs` and `buildTypes` as text for now, behind a reader whose output type
distinguishes a literal from an environment variable, from a Gradle property and from an expression
Servante did not evaluate, and add a Tooling API implementation of the same contract once
`repo.gradle.org` is a reachable dependency source.

## Consequences

- Positive: a password written as a literal, which is the finding that matters, is detected exactly; so
  is a password correctly read from the environment, which must *not* be reported.
- Positive: the honest-reporting rule is kept at the type level. There is no code path that turns an
  unevaluated expression into a value.
- Negative: a project whose keystore path or alias is computed shows "Servante read this and did not
  evaluate it" where a resolved value belongs. The matrix then reports that key as unknown, which is
  correct but less useful than the real answer.
- Negative: `SigningConfigReader` will be one of two implementations of the same job for a while. The
  Tooling API one supersedes it; this record is superseded with it.
- Follow-up: `docs/notes/signing-config-reading.md` lists, case by case, what each approach can see, so
  the swap is a known quantity rather than a rewrite. Revisit as soon as the dependency resolves.
