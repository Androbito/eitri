# Prompt 1: UI design (Stitch or Claude Design)

Paste everything below the line into Stitch or Claude Design. Stitch works better screen by screen: send the "Product", "Visual direction" and "Shared layout" sections first, then one screen section per request. Claude Design can take it whole.

When the designs are done, export them into `design/` as described in `design/README.md`. Prompt 2 reads them from there.

---

Design a desktop application called **Servante**.

## Product

Servante is a free, open source toolbox for Android and Kotlin developers. It runs on macOS, Windows and Linux. It bundles ten tools in one window. Each tool answers a question a developer would otherwise lose twenty minutes on. The user points Servante at an Android project folder and gets diagnoses, not dashboards for their own sake.

The audience is professional Android developers. They live in Android Studio, read monospace all day, and distrust tools that look like marketing. The app sits close to their signing keys, so it has to look precise, calm and trustworthy.

Core behaviors the UI must make visible:
- It reads by default. Any write (deleting, editing a file, installing) goes through a confirmation that summarizes what will change.
- What cannot be read is shown as "Unavailable" with the reason. Never a placeholder value.
- Every finding has a severity (critical, warning, info, healthy), a plain explanation, a location (file and line, or keystore and alias) and, when possible, a fix.
- AI features are optional and use the user's own API key. Before anything is sent, the user sees what will be sent and to whom.

## Visual direction

- Desktop app, not a website. Dense but breathable, closer to a pro tool than to a consumer app.
- Light and dark themes, both first class. Design every screen in both.
- Base: neutral greys with no color tint. Light: background `#F5F7F5`, surfaces white. Dark: background `#0E1210`, surfaces `#151A17` rising to `#1E2521`.
- Brand and primary color: Android green `#3DDC84`, the green of the official Android brand. Use it for the logo, primary buttons and other fills, always with dark text `#06210F` on top. Where green must work as text, a border or a thin bar on a light background, use the darker shade `#0A7D43`, because `#3DDC84` on white is too faint to read. In the dark theme `#3DDC84` works for both.
- Status colors carry meaning and are never decorative: healthy `#137A43`, warning `#8F5B00`, critical `#C6292F` (lighter variants in dark), unavailable is a neutral grey with a dashed outline. Status is never shown by color alone: a dot always sits next to a word, since healthy and brand are both green.
- Typography: a characterful grotesque for headings (Bricolage Grotesque or similar), a clean sans for UI text (Instrument Sans or similar), JetBrains Mono for fingerprints, versions, paths and code.
- Fingerprints, version numbers and file paths are always monospace and always selectable, with a copy affordance on hover.
- Sentence case everywhere. No all-caps labels. No emoji.
- Corner radius: 10 px on windows and panels, 6 px on controls, 4 px on chips. Hairline borders rather than shadows.
- Icons: simple line icons, 1.5 px stroke.
- Must work at 1280 by 800 minimum and scale to large displays. Window chrome should look native on each OS: draw the macOS variant, and show one screen with Windows chrome and one with Linux (GNOME) chrome.

## Shared layout

- Left sidebar, 232 px: app mark and name, project switcher (current project name, path, a dropdown), then navigation: Overview, Signing, Compose, Gradle, SDK and disk, Preflight, Releases, Reminders, Accessibility, Security, AI review. Each item can show a small status dot or count. Settings pinned at the bottom.
- Main area: a header with the tool name, the question it answers as a subtitle, the time of the last check and a "Check again" button. Content below.
- Optional right inspector, 360 px, that opens when a finding, node or row is selected.
- Finding row component: severity mark, title, one-line explanation, location in monospace, chevron. Expanded state shows the full explanation, the evidence and the fix.
- Diff component for fixes: removed and added lines, file path header, "Copy" and "Open in editor" actions.
- States to design for every tool: first run (nothing checked yet), loading, healthy (nothing wrong, say so plainly), findings, unavailable (missing credentials or tool), error.

## Screens

### 1. Welcome and project picker
First launch. Short statement of what Servante does, "Open a project folder" as the primary action, recent projects, and "Explore with demo data". A small line states: no account, no telemetry.

### 2. Overview
One card per tool in a grid, each showing its headline state for the open project. Examples: Signing "1 fingerprint missing in Firebase", Compose "Score 69, 4 unstable parameters", Gradle "2 incompatible versions", SDK and disk "84 GB, 31 GB reclaimable", Preflight "No bundle checked yet", Releases "1 build in review", Reminders "Target API deadline in 41 days", Accessibility "Score 74, 9 unlabeled controls", Security "Grade C, 2 critical", AI review "No provider configured". Cards are not identical: size them by importance, and the tool with the worst state comes first.

### 3. Signing
The signature screen of the product. Top: the verdict in one sentence, for example "Google Sign-In will fail in release: the Play signing certificate is not registered in Firebase". Center: the fingerprint matrix. Rows are signing identities (debug keystore, upload key, Play app signing key). Columns are consumers (Firebase project, Google Cloud OAuth client, Play Console). Cells show match, missing or unknown. Selecting a row shows the keystore details in the inspector: path, alias, algorithm, key size, valid from and until, SHA-1 and SHA-256 in monospace with copy buttons. Below: findings list, including "Keystore password committed in app/build.gradle.kts line 42". Also design the "Verify a build" flow: drop an APK or AAB, see who signed it and with which signature schemes.

### 4. Compose
Three tabs: Graph, Issues, Compiler report.
- Header with a score out of 100 and six category scores (stability, state, lists, side effects, performance, structure).
- Graph: the composable hierarchy from a chosen root as a node graph, state dependencies as dashed edges, recomposition risk as node color. Selecting a node opens the inspector with its parameters, each marked stable or unstable with the reason.
- Issues: list of findings, one expanded with its fix as a diff.
- Compiler report: a table of composables with restartable, skippable and the unstable parameters.
- A "Deep analysis" button that opens the AI send sheet (screen 12).
- Also a paste mode: an editor on the left, results on the right.

### 5. Gradle
Top: a version strip showing Gradle, AGP, Kotlin, KSP, Compose compiler and JDK as linked chips, with a broken link drawn between two that are incompatible. Below: tabs for Compatibility, Updates (version catalog table with current, latest stable, latest pre-release, and an "Update" action per row), Properties (gradle.properties review) and Build timing (bar list of slowest tasks).

### 6. SDK and disk
A total at the top with a segmented bar by category: system images, emulators, Gradle caches, NDKs, SDK platforms, Kotlin/Native, Studio caches. Below: a tree table with size, last used, "used by this project" and a checkbox. A sticky footer shows the selected total and "Move to trash". Design the confirmation dialog that lists exactly what will be removed.

### 7. Preflight
Empty state is a drop zone: "Drop an AAB or APK". After a file is loaded: a checklist of pass, warn and fail items (target API, 16 KB page size per native library, debuggable flag, exported components, sensitive permissions, R8 mapping), a size breakdown bar, a permissions table showing which library brought each permission, and a "Compare with another build" action that leads to a diff view.

### 8. Releases
Left: list of apps from the connected Play account. Right: for the selected app, one lane per track (internal, closed, open, production). Each release shows version name, version code, status and a rollout progress bar with its percentage. Review status shows "Unavailable from the Play API" where that is the case. Design the "Connect a service account" empty state with a short explanation of the permissions needed.

### 9. Reminders
A timeline of upcoming deadlines (target API requirement, upload certificate validity, service account key age), toggles and reminder windows on the right, and below a history table: the audit log of everything Servante changed, with date, action and target.

### 10. Accessibility
Header with a score out of 100 and six category scores (labels, touch targets, contrast, text scaling, navigation and focus, motion). Left: list of screens of the app with a score each, worst first. Right: findings for the selected screen in the standard finding component, each carrying a WCAG 2.2 criterion chip (for example "1.4.3 Contrast"). A contrast tab shows the theme's text and surface pairs as swatches with their ratio and pass or fail for AA, in light and dark. One finding is expanded with its fix as a diff. Include the "Scan a running screen" action for a connected device, with its unavailable state when no device is attached.

### 11. Security
Header with a grade from A to F, counts by severity and a line stating "A summary of automated checks, not a certification". Findings grouped by OWASP MASVS area (storage, crypto, auth, network, platform, code, resilience), each group collapsible with its count. Each finding shows a MASVS chip and a CWE chip. Secrets are always masked, showing only the first and last characters. A Dependencies tab lists vulnerable libraries with the advisory id, affected range and fixed version, behind a "Check against OSV.dev" action that states what is sent. Include the "Accept with a reason" flow that writes to the baseline file, and export actions for Markdown, HTML and SARIF.

### 12. AI review
- Provider setup: Anthropic, OpenAI, Google, OpenAI-compatible endpoint, Ollama (local). Key field, "Stored in your system keychain" note, model picker, test button.
- Send sheet: review type (architecture, Compose deep analysis, build setup, release readiness, accessibility deep review, security deep review), a file tree with checkboxes showing what will be sent, files excluded automatically because they look like secrets, token and cost estimate, destination clearly named, "Send to Anthropic" as the action label (the provider name changes).
- Results: findings in the standard component with fixes as diffs, each tied to a file and line.
- Agent log: a chronological list of requests, tool calls and responses for the review.
- Local variant: with Ollama selected, a visible "Nothing leaves this machine" state.

### 13. Settings
Sections: General (appearance, language: English, French), Paths (Android SDK, JDK), Accounts (Play service accounts), AI providers, Privacy (a plain list of every network destination the app can contact and why), About (version, license Apache 2.0, GitHub link).

### 14. Shared dialogs
Write confirmation with a summary of changes, keystore password prompt (states that the password is used once and not stored), error dialog with what happened and what to do next.

## Deliverables

- Every screen in light and dark.
- The component sheet: sidebar item, finding row (collapsed and expanded), severity marks, chips, diff block, table, inspector, dialog, empty state, buttons, inputs.
- Design tokens as a list: colors by role for both themes, type scale, spacing scale, radii.
- Realistic fictional content throughout: an app called "Fjord Notes" with package `app.fjord.notes`, never lorem ipsum.
