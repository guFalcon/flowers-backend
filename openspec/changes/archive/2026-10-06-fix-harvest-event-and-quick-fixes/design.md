## Context

See proposal.md (Why). Relevant state:

- `GameResource.harvest` calls `LevelService.harvest(flowerId)`, which returns the yield (0 if the
  flower is unknown or its fill is ≤ 0.1, otherwise `fill² * 100`, and sets fill to 0). The resource
  then publishes `{"type":"harvest","flowerId"}` **iff** `honey == 0`. The condition is inverted.
- The frontend `harvest` SSE handler (`index.html`) already reads `data.flowerId || data.id` and
  `data.fill` (default 0), updates the flower and replays the `depleted` animation. The harvesting
  client also updates its own flower from the REST response, so receiving its own event again is
  harmless (fill 0 → 0, animation replays).
- There is no `src/test` and no `http/` directory yet. `pom.xml` has `quarkus-junit5` and
  `rest-assured`, but no AssertJ.
- `@Scheduled` jobs (`fillFlowers` every 1 s, `publishLevel` every 3 s, `cleanupInactiveBees`) run
  in tests too and would change flower fill while a test is running.

## Goals / Non-Goals

**Goals:** fix the event condition, make the event payload explicit, lay the first test and `.http`
foundation, and apply the two one-line hygiene fixes.

**Non-Goals:** no change to the yield formula, the REST response shape or `level-update`. No
synchronisation work in `LevelService`. That belongs to the thread-safety backlog entry.

## Decisions

### SSE contract

`POST /api/harvest/{flowerId}`: request and response are unchanged.

```
POST /api/harvest/flower-3        →  200 {"flowerId":"flower-3","honey":24.7}
```

`harvest` event on `GET /api/events` (`text/event-stream`, JSON data):

```
before: published when honey == 0   data: {"type":"harvest","flowerId":"flower-3"}
after:  published when honey >  0   data: {"type":"harvest","flowerId":"flower-3","fill":0}
```

- Condition `honey > 0` instead of `honey == 0`. The service already returns 0 for every failed
  case, so no other signal is needed.
- `fill: 0` is added explicitly. The frontend already prefers `data.fill`, and it keeps the event
  self-describing for the later in-place flower update. *Alternative:* omit it and let the frontend
  default to 0. Rejected because the explicit field costs nothing and documents the contract.
- Keep publishing from `GameResource` (not from `LevelService`). This is the smallest change.
  Moving event publishing into the service can happen with the thread-safety work.

### Frontend handler

Replace `data.flowerId || data.id` with `data.flowerId`. No other logic changes are needed. The
old backend never sent `id`.

### QR modal

`closeQrModal()` calls `el.removeEventListener("click", closeQrModal)`. Because the same function
reference is used in `openQrModal()`, the remove call matches the listener that open added.

### Tests

- Add `org.assertj:assertj-core` (test scope; the version is not managed by the Quarkus BOM, so pin
  a current 3.x version).
- In `src/test/resources/application.properties`, set `quarkus.scheduler.enabled=false` so that fill
  stays deterministic during tests.
- `GameResourceHarvestTest` (`@QuarkusTest`): inject `LevelService` and `EventBusService`. Set up a
  flower's fill/rate directly through `levelService.getLevel().getFlowers()`. Subscribe to
  `eventBus.eventStream()` and collect items into a list. Call the endpoint with REST Assured, then
  assert the response body and the collected events with AssertJ. This tests the bus, not the HTTP
  SSE stream. *Alternative:* a real SSE client against `/api/events`. Rejected as unnecessary for
  this change, since the wire format is checked through the `.http` file.
- Call `levelService.restartLevel()` in `@BeforeEach` so each test starts from a fresh level.

### mvnw

`git update-index --chmod=+x mvnw` and `chmod +x mvnw` locally, then remove the note under the
README's dev command.

## Risks / Trade-offs

- [The harvesting client gets its own `harvest` event and replays the depleted animation] → This is
  acceptable: it is visually the same as the REST-driven update. Filtering by sender would need a
  player id in the event. That is out of scope.
- [Disabling the scheduler in tests hides scheduler/harvest interplay] → This is intentional for now.
  Race conditions are tracked in the thread-safety backlog entry.
- [Deploy order] → None. The current frontend handles both old and new events.
