## Context

The backend creates bees in `LevelService.addBeeIfAbsent` under the service's monitor and sends deep
copies of them in every level (`copyLevel`). Each bee already carries `honey`, which is reset on
restart. The harvest SSE event is published by `GameResource.harvest` and today only names the
flower. On the frontend, `level.js` applies loaded and pushed levels (`renderBees`, `showOwnHoney`),
and `events.js` dispatches SSE events. See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**
- Names are assigned and kept by the server alone; clients only display them.
- The leaderboard needs no new endpoint or event type: it is computed in the browser from the
  bees it already receives.

**Non-Goals:**
- No server-side ranking or leaderboard DTO.
- No change to how often `level-update` is published.

## Decisions

### Contract (additive)

Bee in `GET /api/level/{playerId}` and in `level-update`:

```json
{"id":"…","x":0.4,"y":0.5,"targetX":0.6,"targetY":0.5,"color":"#…","lastActive":1759750000000,
 "honey":350,"name":"Willi"}
```

SSE harvest event:

```json
{"type":"harvest","flowerId":"flower-3","fill":0,"beeId":"p1","honey":350}
```

`HarvestResult` (the REST response `{"flowerId","gained","total"}`) is unchanged; `GameResource`
builds the event from `playerId` and `harvest.total()`.

### Name list and picker
A small final class `BeeNames` holds the fixed list (English code, German names as data) and a
static `pick(Set<String> usedNames, Random random)`. `addBeeIfAbsent` collects the names of the
current bees and calls it while holding the lock, so two simultaneous joins cannot get the same
name. Passing the `Random` keeps it testable with a seeded instance; `LevelService` uses one shared
`Random` (it is only used under the lock).

Proposed list (48 names, child-friendly, Biene-Maja characters plus bee/meadow nicknames and classic
German children's names):

Maja, Willi, Flip, Kassandra, Puck, Kurt, Max, Thekla, Summsi, Brummel, Honigtau, Blümchen,
Pollenpaul, Wabenwilma, Nektarina, Flitzi, Sonnenschein, Butterblume, Klee, Löwenzahn,
Gänseblümchen, Hummelchen, Brummbär, Sausewind, Wirbelwind, Pünktchen, Lotte, Paula, Emil,
Fridolin, Krümel, Bommel, Flocke, Mimi, Lilli, Rosalie, Kunigunde, Hugo, Otto, Tilda, Frieda,
Anton, Kasimir, Wuschel, Zitronella, Honigbär, Summselinchen, Pippa

Alternatives considered: letting players type a name (rejected by Gerald: random names avoid
moderation in class); deriving the name from the id hash (deterministic, but cannot guarantee
uniqueness among current bees).

### Exhausted list
With 48 names a normal class never exhausts the list. Beyond that, a random list name plus the
smallest free suffix from 2 (`Maja 2`) keeps names unique without growing the list.

### Frontend
- New module `leaderboard.js` exporting `renderLeaderboard(bees)` and `applyHarvestToLeaderboard(event)`.
  It sorts a copy of the bees (honey desc, then `name.localeCompare(other, "de")`), takes the top 5,
  appends the own row if missing, and builds rows with `textContent` only.
- `applyHarvestToLeaderboard` patches `honey` of the matching bee in `state.levelData.bees` (so the
  next periodic update stays consistent) and re-renders.
- `level.js` calls `renderLeaderboard` and shows the own name wherever it calls `showOwnHoney`;
  `events.js` calls `applyHarvestToLeaderboard` on `harvest` events next to `showHarvest`.
- Panel is a `<div class="leaderboard">` absolutely positioned inside `#playArea` (top-left,
  translucent background), `pointer-events: none`, so clicks reach the play area handler. The own
  name goes into the `.stats` bar (`<span id="ownName">`).

## Risks / Trade-offs

- [Panel covers flowers on small screens] → compact font sized with `clamp()`, translucent
  background, max 6 rows; flowers stay visible and clickable through it.
- [Umlauts in Java source] → sources are compiled as UTF-8 (Quarkus/Maven default); a test asserts
  `Gänseblümchen` round-trips through JSON.
- [Names lost on backend restart or timeout] → accepted; bees are recreated with new names, same as
  their honey being lost today.

## Migration Plan

Additive contract: deploy backend first or both together; an old frontend ignores `name` and the
extra harvest fields. Rollback = revert either repo.
