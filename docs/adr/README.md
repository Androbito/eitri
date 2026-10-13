# Architecture decision records

Why Servante is built the way it is. One decision per file, newest last. An accepted record is never
edited: a decision that changes gets a new record, and the old one is marked superseded with links both
ways. See ADR 0001 and the `adr-writer` skill.

| # | Decision | Status | Date |
| --- | --- | --- | --- |
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted | 2026-10-12 |
| [0002](0002-compose-multiplatform-for-desktop.md) | Compose Multiplatform for Desktop as the UI toolkit | Accepted | 2026-10-12 |
| [0003](0003-module-layout.md) | Module layout: core, tools, ui, app | Accepted | 2026-10-12 |
| [0004](0004-manual-dependency-injection.md) | Manual constructor injection instead of a DI framework | Accepted | 2026-10-12 |
| [0005](0005-sqldelight-for-local-persistence.md) | SQLDelight over SQLite for the audit log, settings and cached results | Accepted | 2026-10-12 |
| [0006](0006-run-detekt-and-ktlint-as-command-line-tools.md) | Run detekt and ktlint as command line tools, not Gradle plugins | Accepted | 2026-10-12 |
| [0007](0007-secret-storage-per-operating-system.md) | Secrets in the OS store, behind one interface, with an in-memory fallback | Accepted | 2026-10-12 |
| [0008](0008-desktop-test-tooling.md) | Desktop equivalents for the Android-only tools in the test strategy | Accepted | 2026-10-12 |
| [0009](0009-read-the-user-s-project-in-the-storage-module.md) | Read the user's project in the storage module | Accepted | 2026-10-12 |
| [0010](0010-read-signing-configs-as-text-until-the-tooling-api.md) | Read signingConfigs as text until the Gradle Tooling API is available | Accepted | 2026-10-12 |
