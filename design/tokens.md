# Design tokens

Source: the "Servante desktop app UI" canvas in Claude Design (private to the owner). The artboard sources are in `screens/`. Every artboard has a "dark" toggle, so one file covers both themes: the light values are on `.servante`, the dark ones on `.servante.dark`, at the top of each file.

## Color roles

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| bg | `#F5F7F5` | `#0E1210` | Window background |
| surface | `#FFFFFF` | `#151A17` | Panels, sidebar, cards |
| raised | `#E9EEEA` | `#1E2521` | Code blocks, table group rows, bar tracks |
| line | `#D3DAD5` | `#303A34` | 1 px borders and dividers |
| text | `#101412` | `#ECF1EE` | Primary text |
| muted | `#55605A` | `#A3AEA8` | Secondary text |
| accent | `#3DDC84` | `#3DDC84` | Android green. Fills only: logo, primary buttons |
| on-accent | `#06210F` | `#06210F` | Text on accent fills |
| accent-ink | `#0A7D43` | `#3DDC84` | Green as text, border, focus ring, progress bar |
| accent-soft | `#DDF7E8` | `#153524` | Selected row, selected option |
| ok / ok-soft | `#137A43` / `#DDF3E6` | `#4FD08A` / `#173A2B` | Healthy |
| warn / warn-soft | `#8F5B00` / `#FBEBCB` | `#F2B453` / `#3D2F14` | Warning |
| crit / crit-soft | `#C6292F` / `#FBE0E1` | `#FF7A7F` / `#44191D` | Critical |
| add / del | `#E3F4E9` / `#FBE4E5` | `#17352A` / `#3E1A1E` | Diff lines |

Rules:
- `accent` is never used for text or thin lines on a light background: it is too faint there. Use `accent-ink`.
- Status is never shown by color alone. A dot always sits next to a word, because healthy and brand are both green.
- Unavailable is not a color: muted text in a dashed 1 px outline, with the reason.
- Chart series, in order: `#0A7D43`, `#3DDC84`, `#2F6FDE`, `#E08A1E`, `#8A938E`.

## Type

| Role | Face | Size / weight |
| --- | --- | --- |
| Display | Bricolage Grotesque | 44 / 700, tracking -0.02em |
| Screen title | Bricolage Grotesque | 26 / 700, tracking -0.01em |
| Panel title, verdict | Bricolage Grotesque | 18 to 20 / 700 |
| Big number | Bricolage Grotesque | 40 to 52 / 700 |
| Label | Instrument Sans | 14 / 600 |
| Body | Instrument Sans | 14 / 400, line height 1.45 |
| Secondary | Instrument Sans | 13 / 400, muted |
| Data | JetBrains Mono | 12 / 400 to 500. Fingerprints, versions, paths, code, standard chips (11) |

## Spacing, shape

- Spacing steps: 4, 8, 12, 16, 20, 28, 32
- Radius: 10 panels and cards, 6 controls, 4 chips
- Borders: 1 px `line`. No shadows
- Sidebar 232 wide. Main padding 28 top, 32 sides. Inspector 280 to 380 wide
- Control heights: 36 default, 32 small, 40 for the one main action of a sheet

## Screens

| File | Screen |
| --- | --- |
| `Welcome.dc.html` | 01 Welcome and project picker |
| `Main.dc.html` | 02 Overview |
| `Signing.dc.html` | 03 Signing |
| `Compose.dc.html` | 04 Compose |
| `Gradle.dc.html` | 05 Gradle |
| `SdkDisk.dc.html` | 06 SDK and disk |
| `Preflight.dc.html` | 07 Preflight |
| `Releases.dc.html` | 08 Releases |
| `Reminders.dc.html` | 09 Reminders |
| `Accessibility.dc.html` | 10 Accessibility |
| `Security.dc.html` | 11 Security |
| `AiReview.dc.html` | 12 AI review |
| `Settings.dc.html` | 13 Settings |
| `Dialogs.dc.html` | 14 Dialogs |
| `Components.dc.html` | Components, states and tokens |

## Known gaps

Each tool screen is drawn in its "findings" state. The other states (first run, loading, healthy, unavailable, error) are drawn once, as patterns, in `Components.dc.html`. Not drawn yet: the "verify a build" flow in Signing, the Issues and Compiler report tabs in Compose, the Compatibility, Properties and Build timing tabs in Gradle, the build comparison in Preflight, the Dependencies tab in Security, Windows and Linux window chrome. 
