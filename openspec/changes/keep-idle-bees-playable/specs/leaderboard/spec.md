## MODIFIED Requirements

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
