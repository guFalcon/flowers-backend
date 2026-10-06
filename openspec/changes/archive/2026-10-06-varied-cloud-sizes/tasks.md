## 1. Backend

- [x] 1.1 In `Weather.java` set `MIN_CLOUDS`/`MAX_CLOUDS` to 12/18, replace `CLOUD_SIZE` by `MIN_CLOUD_SIZE` 0.06 / `MAX_CLOUD_SIZE` 0.16, add `SKY_MIN_X` −1 / `SKY_MAX_X` 2, generate `x` in `[−1, 2]` and a random size per cloud; verify it compiles (`./mvnw compile` with JDK 21)
- [x] 1.2 Change `wrap` to take explicit bounds and wrap `x` over `[−1 − r, 2 + r]` widths in `cloudPositionAt` (`y` unchanged); verify with a new `WeatherTest` case: a cloud drifting right passes `x` 1.5 unwrapped and re-enters at `−1 − r` after `2 + r`
- [x] 1.3 Update `WeatherTest` (count 12–18, `x` in `[−1, 2]`, size 0.06–0.16, sizes not all equal over several generated levels); verify `WeatherTest` passes
- [x] 1.4 Update `GameResourceLevelTest` (count 12–18, size and `x` ranges); verify it passes
- [x] 1.5 Run the full backend test suite and verify it is green (flight/harvest tests with hand-placed clouds must still pass)

## 2. Frontend

- [x] 2.1 In `clouds.js` mirror the new horizontal wrap range (`SKY_MIN_X`/`SKY_MAX_X`, comment pointing to `Weather.java`); verify in the browser console that `cloudPositionAt` returns the same position as the backend test case of 1.2
- [x] 2.2 In `styles.css` drop `overflow: hidden` from `.cloud-layer` (window clipping via `body` stays) and update its comment; verify no scrollbars appear

## 3. Verification

- [x] 3.1 Start backend (quarkus:dev, :8084) and frontend (:8081), open the game headless (Playwright) in a 1400×900 window and verify by screenshots: clouds of visibly different sizes, clouds visible left and right of the play area, a cloud leaving the play area keeps drifting beside it; flowers/bees are still covered by clouds and clicks through clouds still move the bee; stop both servers afterwards
