## Why

Three small defects from the backlog are cheap to fix together. The `harvest` SSE event fires on the
wrong condition: it goes out when a harvest *fails* (`honey == 0`), so other clients wrongly show a
still-full flower as emptied, and after a successful harvest they keep seeing a full flower for up to
3 s until the next `level-update`. Also, `mvnw` is not executable on a fresh clone, and the QR modal
adds click listeners every time it closes instead of removing them.

## What Changes

- Backend: `POST /api/harvest/{id}` publishes the `harvest` SSE event only when the harvest succeeds
  (honey > 0), and the event now always carries `"fill": 0`. A failed harvest publishes nothing.
  This is a behaviour change to the SSE contract. Existing frontends keep working.
- Frontend: the `harvest` event handler stays compatible. Drop the dead `data.id` fallback and rely
  on `flowerId` plus `fill`.
- `http/`: add the first `.http` file, covering `GET /api/level/{playerId}`, `POST /api/harvest/{id}`
  and `GET /api/events`.
- Backend tests: add the first `@QuarkusTest`, covering successful and failed harvests and the
  event they publish (or don't publish).
- Repo hygiene: mark `mvnw` as executable in git (`100755`) and remove the "not executable"
  workaround note from `README.md`.
- Frontend: `closeQrModal()` removes the `[data-close-modal]` click listeners instead of adding them
  again.

## Non-goals

- The double-encoded `level-update` payload, LevelService thread-safety and client/server fill
  drift. These have their own backlog entries.
- Server-side validation of harvests (bee position, honey score). See "Server trusts the client".
- Restructuring `index.html`.

## Capabilities

### New Capabilities
- `flower-harvest`: harvesting a flower via REST, the honey yield, and the `harvest` SSE event that
  tells every client the flower has been emptied.
- `qr-code-modal`: opening and closing the QR-code modal in the frontend without leaking listeners.

### Modified Capabilities
<!-- none: openspec/specs/ is still empty -->

## Impact

- **Backend** (`flowers-backend`): `resources/GameResource.java` (harvest event condition and
  payload), new `src/test/java/...` with the first `@QuarkusTest`, new `http/game.http`, `mvnw` file
  mode, `README.md`.
- **Frontend** (`flowers-frontend`): `index.html` (`harvest` SSE handler, `closeQrModal()`).
- **Deploy/CI**: none. Both pipelines redeploy on push as usual. CI runs no tests.
- **SSE contract**: same `harvest` event shape plus a `fill` field. It now fires on success only.
  The deployed frontend handles both old and new behaviour, so deploy order does not matter.
