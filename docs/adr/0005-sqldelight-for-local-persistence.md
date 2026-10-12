# 0005. SQLDelight over SQLite for the audit log, settings and cached results

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

Servante has to keep three kinds of local state: an audit log of every write it performs, which must be
append-only and queryable by date and project; user settings; and cached tool results so reopening a
project does not re-run every check. The audit log is the one Servante's own product rules depend on, so
its schema has to be migratable without losing rows. There is no server and no sync.

## Options considered

1. **SQLDelight over the JDBC SQLite driver**: Pros: the schema is `.sq` files, so a migration is a
   reviewable file and SQLDelight verifies it against the previous schema at build time; queries are
   type-checked at compile time, so a renamed column breaks the build rather than a user's history; returns
   Flows for the UI; one well-understood file to back up. Cons: a Gradle plugin and a code generation step.
2. **Plain JSON files on disk**: Pros: no dependency, trivial to inspect. Pros stop there. Cons: an
   append-only log in JSON means rewriting the whole file per event, which loses history on a crash mid
   write; no queries; every schema change is hand-written migration code.
3. **Exposed or another JVM ORM**: Pros: mature, flexible. Cons: schema lives in Kotlin, so migrations are
   hand-written and unverified; queries are checked at runtime, not at compile time.
4. **Room KMP**: Pros: familiar to Android developers, compile-time checked queries. Cons: pulls androidx
   dependencies onto a desktop-only classpath for no benefit here, and its desktop target is newer and
   less proven than SQLDelight's JVM driver.

## Decision

We will use SQLDelight with the JDBC SQLite driver for the audit log, settings and cached results, because
a verified migration path matters more than anything else for a log the product promises to keep, and
because compile-time checked queries catch a schema mistake before it reaches a user's database.

## Consequences

- Positive: a schema change that would drop data fails the build.
- Positive: the audit log, which `WriteGate` writes to on every write Servante performs, is queryable for
  the Reminders history view in M6 with no extra storage.
- Negative: generated code, so a schema edit means a build before the new query compiles.
- Negative: one database file to locate per OS. The path sits behind a platform interface in `core`.
- Follow-up: secrets never go in this database. See ADR 0007.
