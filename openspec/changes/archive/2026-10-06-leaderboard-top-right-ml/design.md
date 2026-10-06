## Context

See proposal.md for the motivation. Current state:

- Backend: `LevelService.HONEY_PER_FULL_FLOWER = 1_000`, `gained = Math.round(fill² × 1000)`, honey is a
  `long` on `Bee` and in `HarvestResult`.
- Frontend: `index.html` has `<div class="stats">` (own name + `Honey: <n>`) fixed at the top right of
  the page, filled by `honey.js` (`showHoney`, `showOwnHoney`, `showOwnName`) from `level.js` and
  `harvest.js`. The leaderboard `<div id="leaderboard">` lives inside `#playArea`, absolutely
  positioned top-left with `z-index: 4000` and `pointer-events: none`.
- `#playArea` is a centred portrait column (9:16) starting 60 px below the top; it has a `transform`
  and therefore forms its own stacking context. Flowers have no explicit z-index, bees have 5000.

## Goals / Non-Goals

**Goals:**
- Honey in integer µl with a single constant change on the server.
- Leaderboard at the top-right of the page, painted below flowers and bees, never catching clicks.

**Non-Goals:**
- No new fields, no float honey in the contract, no change to ranking or harvest rules.

## Decisions

### Contract: integer microlitres, same shapes
The JSON shapes stay exactly as they are; only the meaning and size of the numbers change:

```
POST /api/player/{playerId}/harvest  → {"flowerId": "flower-3", "gained": 13, "total": 113}
GET  /api/level/{playerId}           → bees[i]: {..., "honey": 113, "name": "Maja"}
SSE harvest                          → {"type":"harvest","flowerId":"flower-3","fill":0,"beeId":"p1","honey":113}
```

`HONEY_PER_FULL_FLOWER` becomes `50` (µl). A honey bee's crop holds about 40 µl of nectar, so a full
flower ≈ one load is plausible and keeps the game balance (fill² curve) unchanged.
*Alternative:* `honey` as a JSON double in ml — rejected: rounding noise in sums (`0.30000000000000004`)
and a type change on `Bee`/`HarvestResult`/the event for no gain. Display formatting belongs in the
client. The minimum successful harvest (fill just above 0.1) still yields `round(0.5) = 1` µl, so a
successful harvest never gains 0 and the "publish only if gained > 0" rule stays intact.

### Frontend formatting
`leaderboard.js` gets a small `formatHoney(microlitres)` → `(µl / 1000).toFixed(2) + " ml"`. Fixed
`.` separator (the UI is English), tabular numbers already set on `.honey`.

### Leaderboard position and stacking
Move `#leaderboard` out of `#playArea` to `<body>`, placed **before** `#playArea` in the DOM, styled
`position: fixed; top: 10px; right: 12px; z-index: 0; pointer-events: none` (the old `.stats` spot).
`#playArea` (positioned, `transform`, z-index auto → painted as a level-0 stacking context in DOM
order) comes later in the DOM and is therefore painted above the leaderboard, with its flowers and
bees. `pointer-events: none` lets clicks fall through to `#playArea` where they overlap; outside the
play area there is nothing to click anyway. The `body::before` tint (also z-index 0) precedes both,
so the panel stays above the tint.
*Alternative:* keep it inside `#playArea` at the top-right with a z-index below flowers — rejected:
on a desktop screen the play area is a narrow centred column, so the panel would sit on the meadow
instead of in the free page corner the user asked for.

### Removing the own display
Delete `honey.js` and the `.stats` markup/CSS; `level.js` drops `showOwnHoney`/`showOwnName`,
`harvest.js` drops `showHoney(json.total)` and keeps the slurp/bump logic. The own honey reaches the
leaderboard via the `harvest` SSE event (already handled by `applyHarvestToLeaderboard`).

## Risks / Trade-offs

- [Own row lags by the SSE round-trip instead of updating from the harvest response] → The event is
  published synchronously during the harvest request, so it arrives roughly with the response; no
  extra handling needed.
- [On a narrow phone the fixed panel covers the top-right of the meadow] → Accepted and specified:
  flowers and bees are drawn above it and clicks pass through.
- [Mixed deploy (old frontend/new backend) shows tiny "points"] → Push backend and frontend together.

## Migration Plan

No persisted state (in-memory only): after the redeploy all bees start with 0 µl. Push both repos in
one go; rollback is reverting both commits.
