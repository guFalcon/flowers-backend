## 1. Baseline

- [x] 1.1 Start the backend (`quarkus:dev`, :8084) and the frontend (`npm start`, :8081); write a headless Playwright script (scratchpad) that routes the frontend to the local backend and checks: page loads without console/page errors, own bee appears, connection status shows connected, a click flies the bee and a harvest on a filled flower raises the honey counter, a second browser context sees the flower empty (harvest event), QR modal opens/closes via button and Escape repeatedly, `?admin=true` restart rebuilds the flowers. Run it against the current code and save screenshots as the baseline — all checks pass.

## 2. Frontend — library classes

- [x] 2.1 Turn `bee.js`, `sse-connection.js`, `audio-system.js` into ES modules (`export class …`, drop the `window.` / `module.exports` tails); verify with `grep -n "window\.\(Bee\|AudioSystem\|SSEConnectionManager\)\|module.exports"` returning nothing.

## 3. Frontend — split the inline script

- [x] 3.1 Create `config.js`, `state.js`, `layout.js`, `audio.js` with the code moved verbatim from `index.html` (state via the shared `state` object); verify each file has only the imports listed in design.md.
- [x] 3.2 Create `flowers.js`, `bees.js`, `level.js` (rendering, `fetchLevel`, `init`, `level-update` handling); verify no module imports one that imports it back (`grep -n "^import"` per file).
- [x] 3.3 Create `events.js`, `harvest.js`, `admin.js` (SSE dispatch, click → fly → harvest + honey counter, QR modal/admin panel with restart callback); verify the same import check.
- [x] 3.4 Create `main.js` wiring everything in the original startup order; reduce `index.html` to markup plus `<script type="module" src="main.js"></script>` and verify it contains no other `<script>`.

## 4. Docs

- [x] 4.1 Frontend `README.md`: local-backend switch now in `config.js`, note that the page must be served over HTTP (modules), update the file table with the new modules; verify by reading the rendered section.
- [x] 4.2 Backend repo: replace "`SERVER` constant in `index.html`" with `config.js` in `.claude/CLAUDE.md`, `openspec/config.yaml`, `ai/memory/project_flowers.md`, `ai/memory/feedback_ui_tests_myself.md`, `ai/memory/feedback_contract_both_repos.md`; update the headless-browser recipe in `ai/memory/reference_build_and_test.md` (route `config.js`, no top-level functions via `page.evaluate`); verify `grep -rn "index.html" .claude ai openspec/config.yaml` shows no stale reference to the URL constant.

## 5. Verification

- [x] 5.1 Re-run the baseline Playwright script from 1.1 (adapted to route `config.js`) against the refactored frontend — all checks pass, no console/page errors, screenshots match the baseline.
- [x] 5.2 Build the frontend image (`docker build -t flowers-frontend:local .`) and verify every new module is in `/app` (`docker run --rm flowers-frontend:local ls /app`).
- [x] 5.3 Stop every server/container started for this change and verify with `ss -ltn | grep -E '8081|8084'` and `docker ps` that nothing is left.
