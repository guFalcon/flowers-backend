---
name: reference_build_and_test
description: Dev/test/build commands for flowers-backend (Maven/Quarkus) and flowers-frontend (Express), .http files and headless-browser checks
metadata:
  type: reference
---

**Not yet verified for flowers** (taken from the READMEs/configs on 2026-10-05) unless marked
otherwise — mark each line verified once it has run.

- **Backend tests (verified 2026-10-06):** `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw test`
  (153 test runs incl. repeated `WeatherTest`, ~20 s); single class `-Dtest=GameResourceHarvestTest`, single method
  `-Dtest='LevelServiceConcurrencyTest#harvestAndGrowthDoNotInterleave'`.
  `src/test/resources/application.properties` disables the scheduler (call `fillFlowers`/
  `cleanupInactiveBees`/`publishLevel` directly) and sets `quarkus.http.test-port=0` (random port, no
  clash with the frontend dev server on 8081; REST Assured and `@TestHTTPResource` follow it).
  Test classes: `resources/GameResourceHarvestTest` (harvest), `resources/GameResourceLevelTest`
  (join/rejoin, snapshot, target, restart), `resources/EventStreamTest` (real SSE via
  `java.net.http.HttpClient`, retries the harvest until the subscriber is registered),
  `services/LevelServiceCleanupTest` (uses package-private `cleanupInactiveBees(long now)`),
  `services/LevelServiceConcurrencyTest` (stress test + harvest/growth interleaving). Bees survive
  between tests (application-scoped, no reset): use unique `UUID` player ids, assert only on own bees.
  Clouds (since clouds-slow-bees): flight/harvest timing tests call `levelService.useWeather(Weather.none())`
  AFTER any `restartLevel()` (restart regenerates clouds) and restore a generated `Weather` in `@AfterEach`,
  because the bean is shared with the REST tests that expect 4–6 clouds. Cloud tests build
  `new Weather(clouds, List.of(new WindKeyframe(T0, angle)), T0 + 15_000)` (no wind turn for 15 s).
  `placeArrived` starts 30 s ago (slowed diagonal ≈ 20 s).
  REST Assured sends a form content type on an empty POST → 415 against the class-level
  `@Consumes(JSON)`; set `.contentType(ContentType.JSON)` (browsers send none, which Quarkus accepts).
- **Backend dev (verified 2026-10-06):** start in the background with
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw quarkus:dev -Dquarkus.console.enabled=false`, wait for
  `Listening on: http://localhost:8084` in the log. Dev UI at `/q/dev/`, Swagger UI at
  `/q/swagger-ui` (not yet checked); no Dev Services (no database). Stop with `pkill -f quarkus:dev` in its own call
  (it also matches the invoking shell → exit 144), then check `ss -ltn | grep 8084`.
- **Backend image:** `./mvnw package -DskipTests && docker build -f src/main/docker/Dockerfile.jvm -t flowers-backend:local .`
- **Frontend dev:** `cd ~/source/htl/js/flowers-frontend && npm ci && npm start` (nodemon) →
  http://localhost:8081 (`INTERNAL_PORT` overrides). For a local backend switch the `SERVER`
  constant in `config.js` or reroute in Playwright (preferred: no file change). CORS already
  allows `http://localhost:8080` and `:8081`.
- **Frontend CI check:** the shared `npm-build-workflow` runs `npm ci`, so `package-lock.json` must
  stay in sync with `package.json`; verify with `docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD":/app -w /app node:24 sh -c "npm ci && npm run build"`
  (verified 2026-10-06; `--user`/`HOME` keep `node_modules` owned by Gerald). Regenerate the lock file
  the same way with `npm install`.
- **Frontend image (verified 2026-10-06):** `docker build -t flowers-frontend:local .` then
  `docker run --rm -e INTERNAL_PORT=8080 -p 8080:8080 flowers-frontend:local`.
- **.http files (verified for flowers 2026-10-06 with `http/game.http`, plain `cd http && docker run --rm --network host -v "$PWD":/workdir jetbrains/intellij-http-client game.http`
  using an in-file `@host`; in-file `@vars` are NOT visible to `request.variables.get` or `{{…}}` inside
  `> {% %}` handlers — use literals there; the trailing SSE request terminates on its own):** `cd http && docker run --rm --network host -v "$PWD":/workdir
  jetbrains/intellij-http-client --env-file http-client.env.json --env dev *.http`;
  `-V baseUrl=…` overrides the env file. `client.test(...)` callbacks run after the handler body,
  so read a global into a `const` before a later `client.global.set` overwrites it.
  SSE checks (verified 2026-10-06): `response.body.onEachLine((line, unsubscribe) => …)` gets raw
  lines (`data:{…}`, blank lines) — strip the `data:` prefix before `JSON.parse`. Inside that callback
  `client.test()`/`client.log()` are silently ignored by the CLI; `throw new Error(…)` fails the run
  (exit 1), so assert by throwing, then `unsubscribe()`.
- **Headless browser (verified for flowers 2026-10-06, after the ES-module split):** rewrite
  `SERVER` by routing `config.js` (`context.route(url => url.pathname === '/config.js', …)`,
  `route.fetch()` + string replace of the live URL + `route.fulfill`) — routing the SSE request
  itself would buffer the stream. Also fail on any request to the live backend so a missed route
  is noticed. The code lives in ES modules, so its functions are NOT globals in `page.evaluate`:
  drive the UI through the DOM (or import `/state.js`, `/bees.js`, `/clock.js`, `/clouds.js` in
  `page.evaluate` — same module instances as the app; bee positions = wrapper `style.left/top` + half
  size, the transform is only jitter) (clicks, `#honey`, `.flower[data-id]`, `.bee-tint-wrapper.is-self`,
  `#connectionStatus.connected`, `#qrModal.open`) or `await import('/flowers.js')` inside
  `page.evaluate`. A MutationObserver on `#playArea` catches the transient `.center.depleted`
  flash of a `harvest` event (the next `level-update` rebuilds the flowers).
  Base recipe (from presserl): `mcr.microsoft.com/playwright:v1.55.0-noble`,
  `npm i playwright@1.55.0` in a scratch dir, `docker run --rm --network host -v <dir>:/work -w /work
  mcr.microsoft.com/playwright:v1.55.0-noble node script.js`. The game renders as plain DOM elements
  (no canvas), so normal locators work; use screenshots plus the backend's API/SSE to judge state. Audio needs a user
  gesture — click once before expecting sound-dependent code paths.
- **SSE by hand:** `curl -N http://localhost:8084/api/events`.

See [[reference_machine_jdk]].
