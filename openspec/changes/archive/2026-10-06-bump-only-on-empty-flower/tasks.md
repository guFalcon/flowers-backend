## 1. Backend

- [x] 1.1 No code change: confirm that `POST /api/player/{id}/harvest` returns `flowerId` null off every flower and the flower's id with `gained` 0 on an empty flower, by pointing at the existing tests in `LevelServiceHarvestTest` / `GameResourceHarvestTest` (no new test needed)
- [x] 1.2 Update `docs/diagrams/game-round.puml` so the harvest `alt` has three branches (gained > 0 → slurp; on a flower but nothing gained → bump; `flowerId` null → nothing) and re-render `game-round.svg` via plantuml.unterrainer.info; verify the SVG contains the new branch labels

## 2. Frontend

- [x] 2.1 In `harvest.js`, play the bump and vibrate only when the response is ok, `gained` is 0 and `flowerId` is not null; give no feedback for `flowerId` null or a failed request; verify by reading the diff that the slurp path is unchanged
- [x] 2.2 Update `README.md` (gameplay bullet and the harvest step of the flow) to say the bare meadow is silent and the bump means an empty flower; verify with `grep -n bump README.md`

## 3. Verification

- [x] 3.1 Run backend (`quarkus:dev`) and frontend locally, drive the own bee headless with Playwright while recording `audioSystem.play` calls: flight to the bare meadow → no `bump`/`slurp`; flight to a filled flower → `slurp`; a second flight onto that now empty flower → `bump`; stop both servers and verify ports 8084/8081 are free
- [x] 3.2 Run `openspec validate bump-only-on-empty-flower --strict` and verify it reports the change as valid
