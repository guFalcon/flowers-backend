## 1. Backend

- [x] 1.1 In `GameResource.harvest`, publish `{"type":"harvest","flowerId":<id>,"fill":0}` only when `honey > 0`; verify with the tests in 1.4
- [x] 1.2 Add `org.assertj:assertj-core` (test scope, pinned 3.x) to `pom.xml`; verify `./mvnw -q dependency:resolve` succeeds (JAVA_HOME=java-21)
- [x] 1.3 Add `src/test/resources/application.properties` with `quarkus.scheduler.enabled=false`; verify that flower fill does not change during a test
- [x] 1.4 Add `GameResourceHarvestTest` (`@QuarkusTest`, REST Assured + AssertJ) covering: filled flower → honey > 0, fill 0, exactly one harvest event with `fill` 0; flower with fill ≤ 0.1 → honey 0, fill unchanged, no event; unknown id → honey 0, no event. Verify `./mvnw test` is green
- [x] 1.5 Make `mvnw` executable (`git update-index --chmod=+x mvnw`, `chmod +x mvnw`) and remove the "not executable" note from `README.md`; verify `git ls-files -s mvnw` shows `100755` and `./mvnw -v` runs

## 2. Frontend

- [x] 2.1 In the `index.html` `harvest` SSE handler, use `data.flowerId` only (drop the `data.id` fallback); verify in 4.2
- [x] 2.2 In `closeQrModal()`, call `removeEventListener("click", closeQrModal)` on `[data-close-modal]` elements; verify in 4.3

## 3. HTTP requests

- [x] 3.1 Create `http/game.http` with `GET /api/level/{playerId}`, `POST /api/harvest/{flowerId}` (existing flower, then the same flower again, then an unknown id) and `GET /api/events`, using a `@host = http://localhost:8084` variable; verify by running it against `quarkus:dev`: the first harvest returns honey > 0, the repeat and the unknown id return 0, and the event stream shows exactly one `harvest` event with `fill: 0`

## 4. Verification

- [x] 4.1 Run the backend test suite (`./mvnw test`) and check that it is green
- [x] 4.2 Start the backend (`quarkus:dev`, :8084) and the frontend (:8081, SERVER pointing at localhost). Use headless Playwright with two browser contexts: context A harvests a filled flower, context B shows that flower empty before the next `level-update`, and a failed harvest by A does not empty the flower in B
- [x] 4.3 Headless Playwright: open and close the QR modal 3× (button, backdrop, Escape), then click the backdrop while the modal is closed. Verify that the close handler is not called (instrument via `page.evaluate`) and that `aria-hidden` stays `true`
- [x] 4.4 Stop all servers started for verification (8084, 8081) and confirm with `ss -ltnp`
