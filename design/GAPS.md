# Gaps between the designs and the implementation

What `design/` does not draw, and what Servante does instead. Written while implementing rather than
afterwards, so the list is what was actually missing and not what someone remembered later.

The rule from `prompts/02-build.md`: where a design is silent, follow the nearest existing pattern and
list the gap here instead of improvising a new one. Nothing in this file is a licence to invent a
pattern; each entry says which existing pattern was reused.

## Listed as not drawn in `design/tokens.md`

These are the gaps `tokens.md` itself declares. Each is implemented from the nearest pattern and will be
replaced when the screen is drawn.

| Gap | What is built instead |
| --- | --- |
| The "verify a build" flow in Signing | The findings pattern from `Components.dc.html`, with the signature schemes as a label row and the signer certificate in the inspector where the selected identity goes. No new pattern |
| The Issues and Compiler report tabs in Compose | Not started: M2 |
| The Compatibility, Properties and Build timing tabs in Gradle | Not started: M3 |
| The build comparison in Preflight | Not started: M5 |
| The Dependencies tab in Security | Not started: M7 |
| Windows and Linux window chrome | The system decorations, with the sidebar and content as drawn. macOS keeps the drawn chrome. Recorded as unverified until it is seen on each system |

## Found while implementing M0 and M1

| Gap | Where it came up | What is built instead |
| --- | --- | --- |
| The six tool states are drawn once as patterns, never per tool screen | Every tool screen | One set of state composables in `ui`, parameterised, so a tool screen declares its states rather than drawing them. The patterns in `Components.dc.html` are the reference |
| No empty state for the recent projects list | `Welcome.dc.html` shows three projects | The same list, with one line of muted secondary text where the rows would be. No new pattern |
| No state for "this folder is not a Gradle project" | The project picker | The unavailable pattern, dashed outline and muted text with the reason, placed where the recent list is |
| The fingerprint matrix has no drawn state for a row whose fingerprints could not be read | `Signing.dc.html` draws match, missing and not-used | The unavailable pattern inside the cell: muted text in a dashed outline reading `unknown`, with the reason in the inspector. This is the case where a user skipped a keystore password |
| No drawn cell for "these two cannot be compared" | `Signing.dc.html` | Same unavailable cell as above. It is a real case, not a hypothetical: `google-services.json` records SHA-1 and the Play API exposes SHA-256, so the most important row of the matrix often cannot be compared. See `docs/notes/play-signing-cert.md` |
| The keystore password dialog is drawn for one keystore, `release.jks` | `Dialogs.dc.html` | The same dialog, once per keystore that needs one, with the file name in the title. A "skip this keystore" answer is already drawn, which is what makes a sequence of them bearable |
| No drawn surface for the secret store being unavailable | Settings | A line of muted secondary text under the AI providers section, with the reason from `SecretStore.availability()`. This is the Linux-without-libsecret case, which the product rules require to be visible |
| Focus ring is drawn on a button only | `Components.dc.html` shows a focused button | The same 2 px `accent-ink` outline with a 2 px offset on every focusable control, which is what full keyboard navigation needs |
| No drawn disabled state for a text field or a checkbox | `Components.dc.html` draws a disabled button | The disabled button's treatment: `raised` background, `muted` text, no border change |
| Relative times are shown as "2 minutes ago" with no rule given | Every tool header | One formatter in `ui`, localised, with the thresholds written in its documentation so both languages round the same way |

## Not a gap: deliberate departures

| Where | The designs show | Servante does | Why |
| --- | --- | --- | --- |
| `Signing.dc.html`, Play column for the upload key | `Match` | `unknown`, with the reason that the Play Developer API does not expose the upload certificate | Showing a match would be showing a value Servante did not read. The product rule is that nothing is invented |
| `Signing.dc.html`, OAuth client column | `Match` for debug and upload | Computed from the Android OAuth clients in `google-services.json`, which can differ from the Firebase column | A package registered in Firebase with no Android OAuth client is a real cause of Google Sign-In failing, and the two columns exist to tell those apart |
