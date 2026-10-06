## MODIFIED Requirements

### Requirement: Joining returns the level with the player's own bee
`GET /api/level/{playerId}` SHALL respond with `200` and a JSON level containing `aspect`,
`flowers` (between 6 and 11 flowers), `bees` and `yourBeeId` equal to `playerId`. If no bee with that
id exists, the backend SHALL create one at a random position, standing still (its `targetX`/`targetY`
equal to its `x`/`y`), with `honey` 0 and a random name (see leaderboard), and include it in `bees`.
Repeating the request with the same `playerId` SHALL NOT create a second bee, SHALL NOT change its
position, target, honey or name, and SHALL mark the existing bee as active, so the inactivity timeout
starts over as it does after steering.

#### Scenario: New player joins
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists
- **THEN** the response is `200`, `yourBeeId` is `p1`, `bees` contains exactly one bee with id `p1`, and `flowers` has 6 to 11 entries

#### Scenario: New bee stands still
- **WHEN** a client calls `GET /api/level/p1` and no bee `p1` exists
- **THEN** bee `p1` in the response has `honey` 0, `targetX` equal to `x` and `targetY` equal to `y`

#### Scenario: Player rejoins
- **WHEN** the same client calls `GET /api/level/p1` a second time
- **THEN** `bees` still contains exactly one bee with id `p1`, with the same colour, name and honey as before

#### Scenario: Rejoining keeps an idle bee alive
- **WHEN** bee `p1` was last active 50 seconds ago, the client calls `GET /api/level/p1`, and the inactive-bee cleanup runs 20 seconds later
- **THEN** bee `p1` is still in the level

### Requirement: Level changes are broadcast as level-update
The backend SHALL publish an SSE event `{"type": "level-update", "level": <object>}` on
`GET /api/events`, where `level` is the level as a JSON object (with `aspect`, `flowers` and
`bees`, the same shape as the body of `GET /api/level/{playerId}` minus a meaningful `yourBeeId`),
when a new bee joins, when a bee's target is set, when inactive bees are removed, and periodically
every 3 seconds. `level` SHALL NOT be a JSON-encoded string. Every client connected to
`GET /api/events` SHALL receive each published event as a JSON `data:` line.

In every level sent to a client (by `GET /api/level/{playerId}` or `level-update`), each bee SHALL
carry `x`/`y` as its current server-side position at the moment the level was built,
`targetX`/`targetY` as its current target, `honey` as its current honey and `name` as its name.

#### Scenario: Setting a target broadcasts the level
- **WHEN** a client is subscribed to `GET /api/events` and bee `p1` sets a target
- **THEN** the client receives a `level-update` event whose `level` is a JSON object containing bee `p1` with the new target

#### Scenario: Level is sent as an object
- **WHEN** a client is subscribed to `GET /api/events` and a `level-update` is published
- **THEN** the event's `data:` line contains `"level":{` followed by the level's `flowers` and `bees` arrays, not a quoted string

#### Scenario: A harvest reaches an HTTP subscriber
- **WHEN** a client holds an open HTTP connection to `GET /api/events` and another client's bee `p2` successfully harvests a flower
- **THEN** the subscriber receives a `data:` line with `{"type":"harvest","flowerId":<id>,"fill":0,"beeId":"p2","honey":<total>}`

#### Scenario: Level shows the bee in flight
- **WHEN** bee `p1` is half-way on a flight from (0.2, 0.5) to (0.6, 0.5) and a level is built
- **THEN** bee `p1` in that level has `x` 0.4, `y` 0.5, `targetX` 0.6 and `targetY` 0.5

#### Scenario: Level carries the honey
- **WHEN** bee `p1` has harvested 250 honey and a level is built
- **THEN** bee `p1` in that level has `honey` 250

#### Scenario: Level carries the name
- **WHEN** bee `p1` is named `Flip` and a level is built
- **THEN** bee `p1` in that level has `name` `Flip`
