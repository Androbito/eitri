# Design

The UI source of truth for the app. `prompts/02-build.md` reads this folder.

- `tokens.md`: colors by role for light and dark, type, spacing, the list of screens and the known gaps.
- `screens/`: one `.dc.html` file per screen, exported from the Claude Design canvas. They are plain HTML with inline styles, so every size, color and spacing value can be read directly. Each has a dark toggle: the theme values are the two CSS rules at the top of the file. `canvas.json` is the canvas layout.

The files need the Claude Design runtime to render, so read them as source. For pictures, open the canvas and use Share, Export, then drop the PNGs next to each screen as `<Screen>-light.png` and `<Screen>-dark.png`.

Designs made in another tool (Stitch, Figma) can be added the same way: one folder or file per screen, plus an update to `tokens.md`.
