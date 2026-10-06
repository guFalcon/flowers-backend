## Context

`index.html` loads three classic scripts (`sse-connection.js`, `bee.js`, `audio-system.js`, which
publish their classes on `window`) and then runs one inline script with ~400 lines of top-level
`const`s, mutable `let`s (`levelData`, `yourBeeId`, `userHoney`) and functions that all see each
other. The Express server serves the repo root statically; the Dockerfile copies `*.js` from the
root into the image. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Each file has one concern and a name a student can guess from the feature ("where is the click
  handled?" → `harvest.js`).
- Dependencies between files are explicit (`import`), no hidden globals.
- Byte-for-byte the same runtime behaviour, including timing (2 s fill interval, flight-timer
  harvest) and request order on startup.

**Non-Goals:**
- Improving any logic while moving it — code moves verbatim apart from the minimum needed to share
  state across modules. Fixes are separate changes (see proposal Non-goals).

## Decisions

### Native ES modules instead of more classic scripts
`<script type="module" src="main.js">` plus `import`/`export`. Every modern browser supports it,
no build step is needed, and dependencies become visible in each file's header.
*Alternative:* several classic `<script>` tags sharing globals — keeps the current style but
leaves the order of tags as the only (implicit) dependency description and keeps everything global.
*Consequence:* modules need to be served over HTTP (`file://` does not work). The game is always
served by Express (dev and Docker), so this is acceptable; README says so.

### Flat layout at the repo root
New modules sit next to `bee.js` etc. instead of a `js/` folder. The Dockerfile's `COPY *.js /app`
already covers them, so no Dockerfile change is needed in this change, and Express serves them
unchanged. *Alternative:* `js/` subfolder — tidier, but needs a Dockerfile edit and moves the
existing files; can follow with the Dockerfile cleanup entry.

### Module split

| Module | Contents (moved from the inline script) | Imports |
|---|---|---|
| `config.js` | `SERVER`, URL constants/builders, `PLAYER_ID` (localStorage) | — |
| `state.js` | one exported mutable object `state = { levelData, yourBeeId, userHoney }` | — |
| `layout.js` | `playArea` element, `resizePlayArea()` | — |
| `audio.js` | `audioSystem` instance, sound registration, first-pointer / visibility / blur / focus handlers | `audio-system.js` |
| `flowers.js` | `updateFill`, `createFlower`, `buildLevel`, passive fill growth (`startFillGrowth()`), harvest flash on a flower | `layout.js`, `state.js` |
| `bees.js` | `beeInstances` map, `renderBees` | `bee.js`, `layout.js`, `audio.js`, `state.js` |
| `level.js` | `fetchLevel`, `init` (resize, fetch, build, render), `applyLevel(level)` for `level-update` | `config.js`, `state.js`, `layout.js`, `flowers.js`, `bees.js` |
| `events.js` | `SSEConnectionManager` setup and the `levelRestarted` / `harvest` / `level-update` dispatch, connection-status element | `sse-connection.js`, `config.js`, `audio.js`, `level.js`, `flowers.js` |
| `harvest.js` | play-area `pointerdown` → set target → flight timer → closest flower → `harvestFlower`, honey counter element | `config.js`, `state.js`, `layout.js`, `bees.js`, `audio.js`, `flowers.js` |
| `admin.js` | QR modal open/close, admin panel / QR button; restart calls a callback passed in | `config.js` |
| `main.js` | entry: wires modules and starts in the current order — `connect()` SSE, mount admin/QR button, `init()`, then resize listener, pointer listener, fill interval | all of the above |

Exact helper names may shift during implementation; the split by concern is the decision.

### Shared mutable state through one `state` object
ES module bindings are read-only for importers, so `levelData = …` from another module is
impossible. A single exported object (`state.levelData = …`) keeps assignments working with the
least rewriting. *Alternative:* getter/setter functions per variable — more code, no gain here.

### No circular imports
`admin.js` needs "restart → reload level", `level.js` lives above it. `main.js` passes `init` as a
callback (`mountAdminOrQrButton({ onRestart: init })`) instead of `admin.js` importing `level.js`.
Same pattern wherever a lower module would need a higher one.

### Library classes become real exports
`bee.js`, `sse-connection.js`, `audio-system.js` get `export class …`; the `window.X =` /
`module.exports` tails are removed. Nothing else uses them (no Node-side import of these files).

## Risks / Trade-offs

- [Modules run in strict mode; a sloppy-mode construct (implicit global, duplicate parameter) in the
  moved code or the three library files would throw] → load the page headless and fail on any
  `pageerror` / console error.
- [Modules are deferred; anything that relied on running before `DOMContentLoaded`] → the inline
  script already ran at the end of `<body>`, so the DOM was complete either way; verify startup.
- [The headless-browser recipe in memory rewrites `SERVER` inside `index.html` and reaches
  top-level functions via `page.evaluate`; both stop working] → route `config.js` instead, drive
  the UI through the DOM (or `await import('/admin.js')` inside `page.evaluate`); update
  `ai/memory/reference_build_and_test.md`.
- [Browser caches an old `index.html` with new/missing modules after deploy] → Express static
  serves with revalidation (ETag, `max-age=0`); a reload picks up both.
- [A silent behaviour change slips in while moving code] → run the same headless scenario before
  and after (join, fly, harvest, second client sees harvest, QR modal, admin restart) and compare.

## Migration Plan

Single commit in the frontend repo, push to `main` redeploys. Rollback: revert the commit. Backend
repo only gets doc/memory updates.
