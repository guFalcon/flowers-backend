## Why

The level sync between server and clients has three overlapping flaws: the `level-update` SSE event
carries the level as a JSON string nested inside JSON (the frontend has to parse it a second time),
every `level-update` (every 3 s plus every player click) tears down and recreates all flower DOM
nodes, and the client grows flower fill every 2 s while the server grows it every 1 s, so the
displayed fill drifts until the next update. Fixing them together gives a clean, cheap sync path
before the server-authority and leaderboard work builds on it.

## What Changes

- **BREAKING (SSE contract)**: `level-update` events carry `level` as a JSON object instead of a
  JSON-encoded string. Frontend and backend change together; there are no other consumers.
- The frontend applies a `level-update` to the existing flowers by id: unchanged flowers keep their
  DOM node and only get their fill and rate updated; flowers whose appearance or position changed
  are replaced, new flowers are added and flowers no longer in the level are removed.
- The frontend predicts flower growth at the server's pace (every 1 s, `rate` per step); the server
  stays authoritative and every `level-update` overwrites the predicted fill.

## Non-goals

- No server-side validation of harvests, no server-side honey score (see backlog "Server trusts the
  client").
- No change to the `harvest` or `levelRestarted` events, the REST endpoints or the level shape
  itself.
- Bee rendering and the relayout of flowers on window resize stay as they are.
- No change to the broadcast interval (3 s) or the growth rates.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `game-session`: the `level-update` payload becomes an object; new requirements for how clients
  apply a `level-update` to existing flowers and how they predict flower fill between updates.

## Impact

- **Backend**: `LevelService` (level-update construction), new/updated `@QuarkusTest` for the SSE
  payload, `http/game.http` (event description), main spec `game-session`.
- **Frontend**: `events.js` (no second parse), `flowers.js` (`buildLevel()` reconciliation,
  growth interval), `level.js` only if the call site needs adjusting.
- **Deploy/CI**: none. Both repos must be pushed together since the event shape changes; an old
  frontend against a new backend would still work (it only parses strings), a new frontend
  against an old backend would not — push backend first or both in one go.
