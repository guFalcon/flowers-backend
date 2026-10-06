## 1. Backend

- [x] 1.1 Set `HONEY_PER_FULL_FLOWER` in `LevelService` to 50 and document honey as integer microlitres on `Bee.honey` and `HarvestResult`; verify `./mvnw compile` succeeds
- [x] 1.2 Update `LevelServiceHarvestTest`, `GameResourceHarvestTest` and any other test with hard-coded honey amounts (fill 0.5 → 13, fill 0.8 → 32, totals accordingly), and add a test that a full flower yields 50 and a flower with fill just above 0.1 yields 1; verify the full backend test suite passes
- [x] 1.3 Update `http/game.http` comments (honey in µl, `gained = round(fill² × 50)`) and run the file against `quarkus:dev` on :8084; verify all requests pass

## 2. Frontend

- [x] 2.1 Remove the `.stats` element from `index.html` and its CSS rules (`.stats`, `.stats .own-name`, `.stats .value`) from `styles.css`; delete `honey.js` and its imports/calls in `level.js` and `harvest.js` (keep the slurp/bump feedback); verify no references to `honey.js`, `showHoney`, `showOwnHoney`, `showOwnName`, `#honey` or `#ownName` remain (grep)
- [x] 2.2 Move `#leaderboard` out of `#playArea` to `<body>` before `#playArea`, and restyle `.leaderboard` as `position: fixed; top: 10px; right: 12px; z-index: 0; pointer-events: none`; verify in the headless browser that the panel is in the top-right corner of the page
- [x] 2.3 Format honey in `leaderboard.js` as millilitres with two decimals and the unit (`850` → `0.85 ml`, `0` → `0.00 ml`); verify in the headless browser that rows show the ml format

## 3. Verification

- [x] 3.1 Headless browser (Playwright) against local backend (:8084) and frontend (:8081), desktop and phone viewport: no top-right name/honey display exists; the leaderboard sits top-right; a harvest updates the own row in ml; on the phone viewport a flower placed under the panel is drawn above it (`elementFromPoint` at the flower centre hits the flower/play area, not the panel) and clicking it lets the bee fly there and harvest
- [x] 3.2 Stop every server started for verification (8084, 8081) and confirm with `ss -ltnp`
