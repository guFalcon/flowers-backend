## MODIFIED Requirements

### Requirement: Joining returns the level with the player's own bee
`GET /api/level/{playerId}` SHALL respond with `200` and a JSON level containing `aspect`,
`flowers` (between 6 and 11 flowers), `bees` and `yourBeeId` equal to `playerId`. If no bee with that
id exists, the backend SHALL create one and include it in `bees`. Repeating the request with the same
`playerId` SHALL NOT create a second bee, and SHALL mark the existing bee as active, so the
inactivity timeout starts over as it does after steering.

#### Scenario: New player joins
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists
- **THEN** the response is `200`, `yourBeeId` is `p1`, `bees` contains exactly one bee with id `p1`, and `flowers` has 6 to 11 entries

#### Scenario: Player rejoins
- **WHEN** the same client calls `GET /api/level/p1` a second time
- **THEN** `bees` still contains exactly one bee with id `p1`, with the same colour as before

#### Scenario: Rejoining keeps an idle bee alive
- **WHEN** bee `p1` was last active 50 seconds ago, the client calls `GET /api/level/p1`, and the inactive-bee cleanup runs 20 seconds later
- **THEN** bee `p1` is still in the level
