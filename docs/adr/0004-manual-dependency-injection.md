# 0004. Manual constructor injection instead of a DI framework

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

`prompts/02-build.md` leaves the choice open: "Koin or manual DI, your choice, keep it light." Servante is a
single-process desktop app with one composition root in `app`. Its graph is shallow: a tool takes a data
source, a `ProcessRunner`, a `WriteGate` and a clock. There is no per-request or per-Activity scope to
manage, and no annotation processing in the build yet.

## Options considered

1. **Manual constructor injection with one `AppContainer` in `app`**: Pros: no runtime dependency and no
   extra build step; a missing collaborator is a compile error rather than a crash on first use; tests
   construct the object under test directly with fakes, which is what `tdd-craftsmanship` asks for; the
   whole graph is readable in one file. Cons: `AppContainer` grows as tools are added and has to be
   maintained by hand.
2. **Koin**: Pros: declarative modules, easy to swap an implementation for demo mode, familiar to Android
   developers. Cons: a wrong or missing binding fails at resolution time, not at compile time; a service
   locator is reachable from anywhere, which weakens the module boundaries ADR 0003 exists to enforce; one
   more runtime dependency for a graph this shallow.
3. **Hilt or another annotation processor**: Pros: compile-time checked. Cons: Hilt is Android-only, so it
   does not apply; any processor adds a KSP step to every module for little gain at this size.

## Decision

We will wire dependencies by hand through constructors, with a single `AppContainer` in `app` as the
composition root, because the graph is small enough that the compiler can check it for us and because
keeping the UI the only place that knows how objects are built protects the module boundaries.

## Consequences

- Positive: no reflection, no resolution failures at runtime, faster start-up, one fewer dependency.
- Positive: demo mode is a different `AppContainer` wiring, built from each tool's `Fake` data source; the
  choice is explicit and visible rather than an overridden binding.
- Negative: `AppContainer` will be a long file by M9. Splitting it per tool keeps it readable.
- Follow-up: revisit if a tool needs scoped or lazily-cycled objects that hand-wiring makes awkward.
