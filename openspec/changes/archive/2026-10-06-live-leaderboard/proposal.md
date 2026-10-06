## Why

Players only see their own honey, so there is no competition between the bees on the meadow. The
server already owns every bee's honey and sends it with each bee in the level; a live leaderboard
visible to every player turns that into a race. Bees only have an id and a colour, so each bee gets
a friendly name that makes the entries recognisable in class.

## What Changes

- When the backend creates a bee, it gives it a random, child-friendly German name in the spirit of
  "Die Biene Maja" (e.g. `Maja`, `Willi`, `Flip`, `Summsi`, `Brummel`) from a fixed list. Names are
  unique among the current bees as long as the list has an unused name; otherwise a number is
  appended (`Maja 2`). A bee keeps its name for its lifetime, including across level restarts; a
  bee that times out and rejoins gets a new random name.
- Every bee in a level (`GET /api/level/{playerId}`, `level-update`) carries a new field `name`.
- The SSE `harvest` event additionally carries `beeId` and `honey` (the harvesting bee's new total),
  so leaderboards update right after a harvest instead of waiting for the next periodic
  `level-update`.
- Frontend: a leaderboard panel over the play area listing the top 5 bees by honey (rank, colour
  dot, name, honey), plus the player's own row when it is not in the top 5; the own bee is
  highlighted. The player's own name is shown next to the honey display. The panel updates on every
  level load, `level-update` and `harvest` event. Clicks pass through the panel to the meadow.

## Non-goals

- Players cannot choose or change their name; no name endpoint, no name input.
- No persistent / all-time high score; the leaderboard is the current round only (restart resets
  honey as today).
- No separate leaderboard endpoint or event; the leaderboard is derived from the level's bees.

## Capabilities

### New Capabilities
- `leaderboard`: random bee names (list, uniqueness, lifetime) and the frontend leaderboard panel
  (ranking, own-bee highlight, own name display, live updates).

### Modified Capabilities
- `game-session`: a newly created bee gets a name; bees in every level carry `name`.
- `flower-harvest`: the SSE `harvest` event carries `beeId` and `honey` in addition to `flowerId`
  and `fill`.

## Impact

- **Backend**: `dtos/Bee.java` (`name`), new name list/picker (e.g. `BeeNames`),
  `services/LevelService.java` (assign name on creation), `resources/GameResource.java` (extended
  harvest event), new/updated tests.
- **Frontend**: new `leaderboard.js` module, `index.html` (panel + own name), `styles.css`,
  `level.js` / `events.js` (wire updates).
- **http/**: `game.http` documents and asserts the `name` field and the extended harvest event.
- **Contract**: additive only (new bee field, extra event fields); old clients keep working.
  Deploy/CI unchanged.
