# 0008. Desktop equivalents for the Android-only tools in the test strategy

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

The `mobile-testing-strategy` skill names the tool for each level of the pyramid, and several are Android
specific: Robolectric or an instrumented runner for integration, Paparazzi or Roborazzi for screenshots,
Espresso or Maestro for journeys, and an in-memory Room database for persistence tests. The skill says to
pick the desktop equivalent and record it. Servante is a JVM desktop app, so every level runs in a plain
JVM, and CI runs on macOS, Windows and Linux where a Linux runner has no display by default.

## Options considered

For the screenshot level, which is the only one with a real choice:

1. **Compose Desktop UI tests rendering to an image, compared against committed PNGs**: Pros: uses the
   toolkit's own test harness, so no extra dependency; renders the real composable; works headless under
   Xvfb on Linux. Cons: text rasterises differently per OS and font stack, so a reference image is only
   stable on the runner that produced it.
2. **Roborazzi or Paparazzi**: Pros: mature, good diff reports. Cons: both are built on the Android
   resource and view pipeline and do not target Compose Desktop.
3. **No screenshot tests, previews reviewed by eye**: Pros: nothing to maintain. Cons: a token or spacing
   regression in the design system reaches a release unnoticed, which is the exact class of bug the
   `ui` module exists to prevent.

## Decision

We will map the pyramid to the desktop as follows, and gate reference images on one platform:

| Level | Skill's Android tool | Servante's equivalent |
| --- | --- | --- |
| Unit | JUnit, kotlinx-coroutines-test, Turbine | the same, on the JVM, with `kotlin.test` |
| Integration, persistence | Room in-memory | SQLDelight over an in-memory JDBC SQLite database |
| Integration, HTTP | MockWebServer | Ktor's `MockEngine`, through the `api-mock-server` mock files |
| Screenshot | Paparazzi, Roborazzi | Compose Desktop UI tests rendering to PNG, references committed per component and state |
| UI, journey | Espresso, Maestro | Compose Desktop UI tests driving the real screens against each tool's `Fake` |

Reference images are generated and compared on the Linux runner only, under Xvfb. macOS and Windows runners
run every other test, including the UI journey tests, which assert on semantics rather than pixels.

## Consequences

- Positive: every level of the pyramid has a tool that runs in CI on all three systems.
- Positive: font rasterisation differences cannot make the build red on a contributor's Mac.
- Negative: a rendering regression that only appears on macOS or Windows is not caught by a screenshot
  test. Journey tests asserting on semantics cover behaviour there.
- Negative: Linux screenshot tests need Xvfb on the runner, which the CI workflow installs.
- Follow-up: if Compose Desktop gains a first-party screenshot testing story, move to it.
