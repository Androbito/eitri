# Design exports

Output of `prompts/01-ui-design.md` goes here. `prompts/02-build.md` reads this folder, so keep the layout:

```
design/
  tokens.md            colors by role (light and dark), type scale, spacing, radii
  components/          one PNG per component, plus HTML if the tool exports it
  screens/
    01-welcome/        light.png, dark.png, and screen.html when available
    02-overview/
    03-signing/
    04-compose/
    05-gradle/
    06-sdk-disk/
    07-preflight/
    08-releases/
    09-reminders/
    10-accessibility/
    11-security/
    12-ai-review/
    13-settings/
    14-dialogs/
```

From Stitch: export each screen as PNG and as HTML (or Figma, then export). From Claude Design: export the page as images, and keep the link to the design in `tokens.md` so it can be reopened.

If a screen has several states (empty, loading, findings), name them `light-empty.png`, `light-findings.png` and so on.
