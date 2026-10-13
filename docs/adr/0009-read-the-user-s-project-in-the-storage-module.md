# 0009. Read the user's project in the storage module

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

`prompts/02-build.md` fixes the module map: `core` is "domain model", `storage` is "SQLDelight database,
SecretStore, settings", and `tools/*` may depend on `core` only. The `mobile-clean-architecture` skill
says the domain layer must not import an I/O library. Project detection needs both: every tool works from
the detected project, and detection is file reading plus a TOML parser. The map has no module named for
it, and the skill forbids putting it in the domain. Rather than invent a module the prompt does not list,
this record says where it goes and why.

## Options considered

1. **The model and the `ProjectDetector` interface in `core`, the file-reading implementation in
   `storage`**: Pros: keeps `core` free of `java.nio` and tomlj, so the domain rule holds; adds no module
   the prompt does not list; `storage` already owns local disk access, since the secret stores shell out
   to the OS and the database is a file. Cons: a module named for storage also reads the user's project,
   which the name does not suggest.
2. **Detection inside `core`, in its own package**: Pros: everything about a project in one module. Cons:
   `core` gains a file system and a TOML dependency, so "the domain compiles with no I/O import" stops
   being true and the rule stops being checkable.
3. **A new `project` module**: Pros: the clearest name. Cons: the prompt's architecture section is
   explicit, and silently adding a module to it is the kind of drift that makes the written plan useless.
   The prompt says to stop and ask rather than pick one silently, and this is small enough to record and
   carry on with.

## Decision

We will keep `AndroidProject`, `GradleModule`, `VersionCatalog` and the `ProjectDetector` interface in
`core`, and put `FileSystemProjectDetector` and the file parsers in `storage`, because the dependency rule
that the domain imports no I/O is worth more than the module name reading perfectly.

## Consequences

- Positive: tools take an `AndroidProject` as input and never reach for the file system themselves, which
  is also what makes them testable with no fixture on disk.
- Positive: `app` and `cli` are the only modules that run detection, once, at the point the user picks a
  folder.
- Negative: `storage` is now "local disk and local database" rather than only the database. Its package
  layout says so: `app.servante.storage.project`, `app.servante.storage.db`, `app.servante.storage.secrets`.
- Follow-up: if a second kind of project reading appears, M4's SDK and disk inventory for example, and
  `storage` starts to feel like a bag, propose a `localio` module in a pull request rather than letting it
  grow quietly.
