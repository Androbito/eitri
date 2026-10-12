# Reading signingConfigs: what text reading sees and what the Tooling API would

Written so that replacing the text reader with a Gradle Tooling API reader is a known change rather than
a rewrite. See `docs/adr/0010`.

| Case in a build file | Text reader | Tooling API |
| --- | --- | --- |
| `storeFile = file("../keystores/release.jks")` | Literal path. Correct | Resolved absolute path |
| `keyAlias = "upload"` | Literal. Correct | Same |
| `storePassword = "hunter2"` | Literal, which is the finding. Correct | Same, and the finding is the same |
| `storePassword = System.getenv("KEYSTORE_PASSWORD")` | Environment variable, not a finding. Correct | Resolved value, which Servante would then have to be careful not to log |
| `keyPassword = providers.gradleProperty("keyPassword").get()` | Gradle property, not a finding. Correct | Resolved value |
| `storeFile = file(keystoreProperties["storeFile"] as String)` | Unresolved expression. Reported as read but not evaluated | Resolved path |
| A signing config applied by a convention plugin in `buildSrc` | Not seen at all. The module's own build file has no `signingConfigs` block, so Servante reports that the module declares none | Seen, with all values resolved |
| A config selected per flavor, `productFlavors { play { signingConfig = ... } }` | Seen only if written inside `buildTypes`; a flavor-level assignment is not read | Resolved per variant, which is the real answer |
| `signingConfig signingConfigs.release` (Groovy) | Read. Correct | Same |
| A config built in a loop over a list of flavors | Not seen | Seen |

## What this means for the user today

A project that writes its signing config literally, which is the great majority, is read correctly and
completely. A project that computes it sees cells marked unknown with the reason, and the Signing screen
says which values Servante could not evaluate. Nothing is guessed in either case.

## What changes when the Tooling API lands

- `SigningConfigReader` gains a sibling implementing the same return type from the configured model.
- An init script, written to a temporary directory and passed with `--init-script`, is how the model is
  read without touching the user's files. The script only reads: it must not add a task, change a
  property, or write anything into the project directory.
- The reader becomes per-variant rather than per-module, because that is what the model exposes and
  because "which key does the play release flavor use" is a question users actually ask.
- A resolved password becomes a value in memory. It has to be wrapped in `Secret` at the boundary, or
  the Tooling API change would quietly undo the "never in a log" rule.
