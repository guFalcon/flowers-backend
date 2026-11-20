# leaderboard Specification

## Purpose

Gives every bee a friendly, child-friendly German name and shows all players a live leaderboard of
the current round's honey, so the bees on the meadow compete with each other.

## Requirements

### Requirement: New bees get a random child-friendly name
When the backend creates a bee, it SHALL assign it a `name` picked at random from a fixed list of at
least 40 child-friendly German bee names in the spirit of "Die Biene Maja" (for example `Maja`,
`Willi`, `Flip`, `Summsi`, `Brummel`). The picked name SHALL NOT equal the name of any other current
bee as long as the list contains a name not in use. If every name in the list is in use, the backend
SHALL pick a random name from the list and append a space and the smallest number from 2 upwards that
makes it unique among the current bees (e.g. `Maja 2`).

A bee SHALL keep its name for as long as it exists, including across level restarts. A bee that was
removed for inactivity and comes back (see game-session) SHALL keep its previous name, unless a
current bee has that name by then; in that case it SHALL get a newly picked name as above. There
SHALL be no way for a client to set or change a name.

#### Scenario: New bee has a name from the list
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists
- **THEN** bee `p1` in the response has a non-empty `name` taken from the name list

#### Scenario: Names are unique
- **WHEN** 10 different players join one after another
- **THEN** all 10 bees have different names

#### Scenario: List exhausted
- **WHEN** every name of the list is already used by a current bee and another player joins
- **THEN** the new bee's name is a list name followed by a space and a number, and no other current bee has that name

#### Scenario: Name survives rejoin and restart
- **WHEN** bee `p1` is named `Willi`, the client calls `GET /api/level/p1` again and the admin restarts the level
- **THEN** bee `p1` is still named `Willi`

#### Scenario: Returning bee keeps its name
- **WHEN** bee `p1` named `Willi` was removed for inactivity, no current bee is named `Willi`, and `p1` joins again
- **THEN** bee `p1` is named `Willi`

#### Scenario: Returning bee's name was taken
- **WHEN** bee `p1` named `Willi` was removed for inactivity, a current bee `p2` is named `Willi`, and `p1` joins again
- **THEN** bee `p1` gets a newly picked name different from every current bee's name

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
