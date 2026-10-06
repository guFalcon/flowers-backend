## MODIFIED Requirements

### Requirement: Level changes are broadcast as level-update
The backend SHALL publish an SSE event `{"type": "level-update", "level": <object>}` on
`GET /api/events`, where `level` is the level as a JSON object (with `aspect`, `flowers` and
`bees`, the same shape as the body of `GET /api/level/{playerId}` minus a meaningful `yourBeeId`),
when a new bee joins, when a bee's target is set, when inactive bees are removed, and periodically
every 3 seconds. `level` SHALL NOT be a JSON-encoded string. Every client connected to
`GET /api/events` SHALL receive each published event as a JSON `data:` line.

#### Scenario: Setting a target broadcasts the level
- **WHEN** a client is subscribed to `GET /api/events` and bee `p1` sets a target
- **THEN** the client receives a `level-update` event whose `level` is a JSON object containing bee `p1` with the new target

#### Scenario: Level is sent as an object
- **WHEN** a client is subscribed to `GET /api/events` and a `level-update` is published
- **THEN** the event's `data:` line contains `"level":{` followed by the level's `flowers` and `bees` arrays, not a quoted string

#### Scenario: A harvest reaches an HTTP subscriber
- **WHEN** a client holds an open HTTP connection to `GET /api/events` and another client successfully harvests a flower
- **THEN** the subscriber receives a `data:` line with `{"type":"harvest","flowerId":<id>,"fill":0}`

## ADDED Requirements

### Requirement: Clients apply level-updates to existing flowers
When the frontend receives a `level-update`, it SHALL match the level's flowers to the flowers on
screen by `id`. A flower whose position, size, petal count and colours are unchanged SHALL keep its
on-screen element and only take over the new `fill` and `rate`. A flower whose position, size,
petal count or colours changed SHALL be redrawn. Flowers new in the level SHALL be added, and
flowers no longer in the level SHALL be removed.

#### Scenario: Periodic update keeps the flower elements
- **WHEN** a client shows the level and receives a `level-update` with the same flowers but different fills
- **THEN** every flower keeps its on-screen element and shows the fill from the update

#### Scenario: Restarted level replaces changed flowers
- **WHEN** the level was restarted and a `level-update` arrives whose flower `flower-0` has a different position and colours than the one on screen
- **THEN** the client redraws `flower-0` at its new position with its new colours

#### Scenario: Flower count changes
- **WHEN** a `level-update` contains fewer or more flowers than are on screen
- **THEN** flowers missing from the update disappear and new flowers appear, so the screen shows exactly the flowers of the update

### Requirement: Clients predict flower growth at the server's pace
Between `level-update` events the frontend SHALL grow each flower's displayed fill by its `rate`
once per second, capped at 1, matching the server's growth step. The fill received in a
`level-update` or `harvest` event SHALL replace the predicted value.

#### Scenario: Fill grows between updates
- **WHEN** a flower with fill 0.2 and rate 0.05 is shown and no event arrives for 2 seconds
- **THEN** the flower shows a fill of about 0.3

#### Scenario: Server value wins
- **WHEN** the client predicted a fill of 0.4 and a `level-update` reports a fill of 0.35 for that flower
- **THEN** the flower shows 0.35 and continues growing from there
