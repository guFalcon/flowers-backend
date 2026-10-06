## Why

Since the live leaderboard shows every bee's name and honey, including a highlighted row for the
player's own bee, the separate "name + Honey" display in the top-right corner is redundant. Its spot
is the natural place for the leaderboard, which currently covers the top-left of the meadow. At the
same time, honey is counted in abstract points (up to 1000 per flower) that quickly grow into large
numbers; a real unit (millilitres, with realistic small amounts) is more tangible for the pupils and
keeps the displayed numbers short.

## What Changes

- The frontend no longer shows the own bee's name and honey in the top-right corner; the own row in
  the leaderboard takes over that role.
- The leaderboard moves to the top-right corner of the page (where the removed display was). It lies
  below the flowers and bees: where it overlaps the meadow, flowers and bees are drawn over it, and
  clicks and taps always reach the meadow, so flowers under it stay harvestable.
- **BREAKING (contract semantics)**: honey is measured in microlitres (µl). The JSON shapes stay the
  same (`honey`, `gained`, `total` remain integers), but a full flower now yields 50 µl instead of
  1000 points: `gained = round(fill² × 50)`.
- The leaderboard shows honey in millilitres with two decimals and the unit, e.g. `0.85 ml`.
- A successful own harvest still plays the harvest sound; the response's `total` is no longer shown
  separately (the leaderboard is updated by the `harvest` SSE event).

## Non-goals

- No change to the JSON shapes of the REST responses or SSE events, no field renames.
- No change to the ranking rules, the top-5 + own-row logic or the bee names.
- No change to the harvest rules (arrival, radius, minimum fill) other than the honey amount.
- No unit switching (µl/ml/l) and no localisation of the number format.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `flower-harvest`: honey is counted in µl and a full flower yields 50 µl; the frontend no longer
  shows the harvest response's `total` as a separate honey display.
- `leaderboard`: the panel moves to the top-right corner of the page, lies below flowers and bees,
  and shows honey in ml; the separate own-name display is removed.
- `game-session`: the separate client-side honey display is removed (the leaderboard covers it).

## Impact

- **Backend**: `LevelService` (honey per full flower 1000 → 50), `HarvestResult`/`Bee` javadoc on the
  unit, updated tests (`LevelServiceHarvestTest`, `GameResourceHarvestTest`, and any test that
  hard-codes 250/640), `http/game.http` comments, main specs `flower-harvest`, `leaderboard`,
  `game-session`.
- **Frontend**: `index.html` (remove `.stats`, move `#leaderboard` out of the play area),
  `styles.css` (drop `.stats`, reposition `.leaderboard`), `honey.js` removed, `level.js` and
  `harvest.js` no longer call it, `leaderboard.js` formats honey as ml.
- **Deploy/CI**: none. Push both repos together: an old frontend against the new backend would show
  the small µl integers as points, a new frontend against the old backend would show e.g. `1.00 ml`
  for a full flower.
