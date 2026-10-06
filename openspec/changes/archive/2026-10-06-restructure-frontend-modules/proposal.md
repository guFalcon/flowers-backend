## Why

The frontend's game logic lives in one ~400-line inline `<script>` in `index.html` that mixes
configuration, layout, flower rendering, bee rendering, SSE handling, input, harvest and the
admin/QR panel. For a teaching project that is hard to read and hard to extend; the planned
leaderboard and clouds features would make it worse. Splitting it into small, named files first
gives those features a clear place to land.

## What Changes

- Move the inline script out of `index.html` into plain browser ES modules at the repo root, one
  concern per file (configuration, shared state, audio setup, layout, flowers, bees, level loading,
  SSE events, click → fly → harvest, admin/QR panel) plus one entry module `main.js`.
- `index.html` keeps only markup and a single `<script type="module" src="main.js">`.
- `bee.js`, `sse-connection.js` and `audio-system.js` become ES modules (`export class …`) instead of
  assigning to `window` / `module.exports`.
- The backend base URL moves from `index.html` to `config.js`; README, project docs and memory are
  updated to point there.
- Still no build step, no framework, no new dependencies.
- No behaviour change: the game looks, sounds and plays exactly as before, including the known
  quirks listed under Non-goals.

## Non-goals

- No REST/SSE contract change and no backend change.
- Not fixing the backlog items that touch the same code: double-encoded `level-update`, client-side
  fill growth drifting from the server, rebuilding all flowers on every `level-update`, client-side
  honey score. They stay in `ai/open-proposals.md` and get their own changes.
- No CSS restructuring, no Dockerfile / dependency cleanup (separate backlog entry).
- No bundler, TypeScript, or test framework in the frontend repo.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none — pure refactor, observable behaviour is unchanged; .openspec.yaml sets skip_specs: true -->

## Impact

- **Frontend** (`flowers-frontend`): `index.html` (script removed), new modules `config.js`,
  `state.js`, `audio.js`, `layout.js`, `flowers.js`, `bees.js`, `level.js`, `events.js`,
  `harvest.js`, `admin.js`, `main.js`; `bee.js`, `sse-connection.js`, `audio-system.js` switched to
  ES module exports; `README.md` (local-backend switch, file table).
- **Backend** (`flowers-backend`): no code change. Docs only: `.claude/CLAUDE.md`,
  `openspec/config.yaml` context and `ai/memory/*` entries that name "the `SERVER` constant in
  `index.html`" now name `config.js`.
- **Deploy/CI**: none expected — the Dockerfile's `COPY *.js /app` already picks up new root-level
  modules; verified by building the image.
- Existing specs (`flower-harvest`, `game-session`, `qr-code-modal`) keep holding; they are
  re-checked in the browser as regression tests.
