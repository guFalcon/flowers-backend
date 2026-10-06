## Context

See proposal.md — Why. Today `LevelService.buildLevelUpdate()` runs `mapper.writeValueAsString(currentLevel)`
under the lock and puts the string into a `Map`; the SSE endpoint then serialises that map again.
The frontend (`events.js`) re-parses the string, `level.js#applyLevel` stores it and calls
`flowers.js#buildLevel()`, which removes every `.flower` and calls `createFlower()` for each.
`startFillGrowth()` adds `rate` every 2 s; the server's `fillFlowers()` adds `rate` every 1 s.
Flower ids are `flower-0 … flower-N` and are reused after a restart.

## Goals / Non-Goals

**Goals:**
- One serialisation of the level, done outside the game-state lock.
- Flower DOM nodes survive periodic updates (no flicker, harvest animation and CSS fill transition
  are not cut off).

**Non-Goals:**
- Repositioning flowers on window resize (existing behaviour, unchanged).
- Interpolating fill smoothly beyond the existing 0.3 s CSS transition.

## Decisions

### Event payload shape

Before:
```json
{"type":"level-update","level":"{\"aspect\":0.5625,\"flowers\":[…],\"bees\":[…],\"yourBeeId\":null}"}
```
After:
```json
{"type":"level-update","level":{"aspect":0.5625,"flowers":[{"id":"flower-0","x":0.31,"y":0.72,"size":0.1,"petals":7,"color":"plum","petalColors":["#…"],"stampColor":"#…","fill":0.4,"rate":0.05}],"bees":[{"id":"p1","x":…,"y":…,"targetX":…,"targetY":…,"color":"#…","lastActive":…}],"yourBeeId":null}}
```
`harvest` and `levelRestarted` are unchanged.

### Snapshot instead of string serialisation under the lock

`buildLevelUpdate()` builds a deep copy of the level under the lock (the same copy logic
`snapshotFor()` uses, factored into one private helper; `yourBeeId` left null) and returns it in
the event map. Jackson serialises it in the SSE writer, outside the lock — which also satisfies the
existing "level SHALL NOT change while being serialised" requirement. The `ObjectMapper` injection
goes away if nothing else uses it.
*Alternative*: publish the live `currentLevel` — rejected, it would be serialised while the game
logic mutates it.

### Reconciling flowers by id

`buildLevel()` builds a map `id → element` from the current `.flower` nodes. For each flower in the
level: if an element exists and its stored signature matches, update `dataset.fill`/`dataset.rate`
and call `updateFill()`; otherwise create a new element (replacing the old one in place if there
was one). Elements whose id is not in the level are removed. The signature is a string stored in
`dataset.signature` at creation: `JSON.stringify([x, y, size, petals, color, petalColors, stampColor,
playArea.clientWidth, playArea.clientHeight])`. The play-area size is part of the signature because
the pixel layout is derived from it: today a resize repositions the flowers implicitly with the next
full rebuild (≤ 3 s), and including the size keeps exactly that behaviour (the next `level-update`
after a resize redraws every flower) while unchanged flowers keep their nodes otherwise.
*Alternative*: compare each attribute against the DOM styles — rejected, pixel values are derived
and lossy. *Alternative*: always replace after `levelRestarted` and only update otherwise —
rejected, a periodic `level-update` can arrive before the `levelRestarted` reload finishes.

### Growth prediction at 1 s

`startFillGrowth()` switches its interval to 1000 ms; the step stays `+rate`, capped at 1. Server
values overwrite it via the reconciliation above and via `showHarvest()`.
*Alternative*: no client prediction, show only server values — rejected, fill would jump by up to
~0.3 every 3 s.

## Risks / Trade-offs

- [Old frontend in browser caches gets an object instead of a string] → `events.js` today already
  accepts a non-string `level`, so old clients keep working.
- [Client and server timers are not phase-aligned, prediction can be up to one step ahead/behind]
  → corrected at most 3 s later by the next `level-update`; acceptable.
- [Signature comparison on floating-point coordinates] → values come unchanged from the same JSON
  source, so equality is exact.

## Migration Plan

Push backend and frontend in the same archive step (backend first). Rollback: revert both commits.
