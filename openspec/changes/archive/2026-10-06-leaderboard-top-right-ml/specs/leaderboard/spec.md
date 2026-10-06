## MODIFIED Requirements

### Requirement: Clients show a live leaderboard
The frontend SHALL show a leaderboard panel in the top-right corner of the page, built from the bees
of the most recent level. It SHALL list the bees ordered by `honey` descending (ties ordered by
`name`), at most the top 5, each row showing the rank (1-based), a dot in the bee's colour, the bee's
`name` and its honey. Honey SHALL be displayed in millilitres: the `honey` value (microlitres)
divided by 1000, with exactly two decimals, a `.` as decimal separator and the unit `ml` (e.g. 850 →
`0.85 ml`). If the player's own bee is not among the top 5, the panel SHALL show it as an additional
row with its actual rank below the top 5. The row of the player's own bee SHALL be visually
highlighted. Names SHALL be displayed as plain text, never interpreted as HTML.

The panel SHALL be rebuilt whenever a level is loaded (`GET /api/level/{playerId}`) or a
`level-update` arrives. When a `harvest` event arrives, the frontend SHALL set the honey of the bee
`beeId` to the event's `honey` and rebuild the panel without waiting for the next `level-update`.

The panel SHALL lie below the flowers and bees: where it overlaps the play area, flowers and bees
SHALL be drawn over it. The panel SHALL NOT capture clicks or taps: they SHALL reach the play area
underneath, so steering and harvesting work on the whole meadow, including flowers under the panel.

#### Scenario: Ranking
- **WHEN** the level contains `Maja` with 300 honey, `Willi` with 500 and `Flip` with 100
- **THEN** the panel shows `1 Willi 0.50 ml`, `2 Maja 0.30 ml`, `3 Flip 0.10 ml` in that order

#### Scenario: Honey in millilitres
- **WHEN** the player's own bee has `honey` 1234
- **THEN** its row shows `1.23 ml`

#### Scenario: Own bee outside the top 5
- **WHEN** the level contains 8 bees and the player's own bee has the least honey
- **THEN** the panel shows the top 5 and below them the player's own bee with rank 8, highlighted

#### Scenario: Harvest updates the panel at once
- **WHEN** another player's bee harvests 13 honey and the `harvest` event arrives
- **THEN** that bee's honey and rank in the panel change right away, before the next `level-update`

#### Scenario: Restart resets the board
- **WHEN** the admin restarts the level and the client reloads the level
- **THEN** every bee in the panel shows `0.00 ml`

#### Scenario: Panel sits in the top-right corner
- **WHEN** the page is loaded and the level contains at least one bee
- **THEN** the panel is shown in the top-right corner of the page

#### Scenario: Clicking through the panel
- **WHEN** the player clicks the meadow where the panel lies over it
- **THEN** the bee flies to the clicked spot

#### Scenario: Flower under the panel stays harvestable
- **WHEN** a flower lies where the panel overlaps the play area and the player clicks that flower
- **THEN** the flower is drawn over the panel, the bee flies to the clicked spot and harvests the flower on arrival

## REMOVED Requirements

### Requirement: Clients show the player's own name
**Reason**: The leaderboard already shows the own bee's name in its highlighted row; the separate
name display in the top-right corner is removed to make room for the leaderboard.
**Migration**: Read the own name from the highlighted leaderboard row.
