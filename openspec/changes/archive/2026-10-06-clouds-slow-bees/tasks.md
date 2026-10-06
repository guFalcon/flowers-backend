## 1. Backend — model and cloud math

- [x] 1.1 Add `dtos/Cloud`, records `dtos/WindKeyframe` and `dtos/PathKeyframe`; extend `Level` with `serverTime`, `clouds`, `wind`; verify `./mvnw compile` (JAVA_HOME=/usr/lib/jvm/java-21-openjdk) succeeds
- [x] 1.2 Implement `services/Weather` (cloud generation, wind schedule generation with holds and stepped turns, `ensureHorizon` with re-anchoring/pruning, `cloudPositionAt` with wrap, `inCloud`, `none()`, `regenerateClouds`); verify with a new `WeatherTest` (plain JUnit + AssertJ): 4–6 clouds with size/speed/drift in range, straight drift (0.5, 0.5) → (0.5, 0.9) after 10 s, wrap at y 1.05 → −0.05, turn steps ≤ 0.5 s apart and ≤ 60° total, turns 15–30 s apart, horizon ≥ 60 s after `ensureHorizon`, re-anchoring leaves future positions unchanged
- [x] 1.3 Replace the flight fields in `Bee` by `path` (immutable list) with `positionAt` along keyframes and `@JsonIgnore arrivalTime()`; adapt `BeeTest` (one-keyframe path, two-keyframe interpolation, multi-segment interpolation) and verify it passes

## 2. Backend — service and resource

- [x] 2.1 `LevelService`: hold a `Weather`, generate it on init, regenerate clouds on restart, call `ensureHorizon` and copy `serverTime`/`clouds`/`wind` in `copyLevel`, add package-private `useWeather`; verify existing tests still pass after switching them to `Weather.none()` and moving `placeArrived` to 30 s ago
- [x] 2.2 `LevelService.updateTarget`: simulate the flight in 50 ms steps against `Weather.inCloud` (35 % speed inside), build the keyframe path (min 200 ms), store it and return it from `setTarget`; harvest uses `arrivalTime()`; verify with new tests in `LevelServiceFlightTest`: flight through a resting cloud at (0.4, 0.5) takes 3.65 s ± 0.1 s with four keyframes and entry/exit near x 0.311/0.489, flight past a cloud takes 2 s with two keyframes, a drifting cloud crossing the line slows the bee, overlapping clouds do not slow further
- [x] 2.3 Harvest timing with clouds: add a test in `LevelServiceHarvestTest` that a harvest at the cloudless arrival time yields 0 and one at the slowed arrival time succeeds; verify it passes
- [x] 2.4 `GameResource.setTarget` returns `{"status":"ok","path":[…]}`; extend `GameResourceLevelTest` (level has `serverTime`, 4–6 `clouds`, `wind` reaching ≥ 60 s ahead, every bee has `path`; target response has a path ending at the target; restart yields new clouds) and `EventStreamTest` (level-update carries `clouds`, `wind`, bee `path`); verify `./mvnw test` passes
- [x] 2.5 Update `http/game.http`: document the new level fields and the target response, assert them in the response handlers; run it against `quarkus:dev` on :8084 and verify all requests pass

## 3. Frontend

- [x] 3.1 Add `clock.js` (`setServerTime`, `serverNow`) and call `setServerTime` in `level.js` for the fetched level and every `level-update`; verify in the headless browser that `serverNow()` is within a few ms of the backend's `serverTime` plus elapsed time
- [x] 3.2 Add `clouds.js` (`cloudPositionAt` mirroring `Weather`, `renderClouds`, rAF loop) and cloud styles in `styles.css` (soft, semi-transparent, above bees, `pointer-events: none`); call `renderClouds` from `level.js`; verify headless: clouds are drawn, move between two screenshots, and `cloudPositionAt` for a given time matches the position computed by the server for the same cloud
- [x] 3.3 Rework `bee.js`: remove the CSS left/top transition (also in `styles.css`), `baseSpeed` and `getTravelDurationInMillis`; add `setPath` with a rAF loop following the keyframes at `serverNow()`, jitter and flight sound while moving; verify headless that a bee's on-screen position follows its path (half-way position at the middle keyframe time)
- [x] 3.4 `bees.js`: apply `b.path` to new bees and to bees whose path changed; verify headless that a joining bee appears at its position without flying in and a reloaded page shows the own bee continuing its flight
- [x] 3.5 `harvest.js`: on click post the target, then `setPath(response.path)` and schedule the harvest at the last keyframe's time via `serverNow()`, guarded by the flight id, no harvest if the target request fails; verify headless: clicking a flower behind a cloud sends the harvest request only at the slowed arrival time and it succeeds, a redirected flight triggers only one harvest

## 4. Verification

- [x] 4.1 End-to-end with `quarkus:dev` (:8084) and the frontend (:8081, `SERVER` pointed at localhost only for the run, not committed): drive two browser sessions headless, check clouds drift identically in both, a bee visibly slows inside a cloud, and harvests work; stop both servers afterwards and verify with `ss`/`ps` that :8084 and :8081 are free
- [x] 4.2 Run `openspec validate clouds-slow-bees --strict` and the full backend test suite once more before archiving
