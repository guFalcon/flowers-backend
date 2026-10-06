## Purpose

Covers how a player harvests honey from a flower via the REST API, and how every connected client
learns over SSE that the flower has been emptied.

## ADDED Requirements

### Requirement: Harvesting a flower yields honey and empties it
`POST /api/harvest/{flowerId}` SHALL respond with `200` and a JSON body `{"flowerId": <id>,
"honey": <number>}`. If the flower exists and its fill is above 0.1, `honey` SHALL be greater than
0 and the flower's fill SHALL be set to 0. Otherwise `honey` SHALL be 0 and the flower SHALL be left
unchanged.

#### Scenario: Harvesting a filled flower
- **WHEN** a client posts to `/api/harvest/{id}` for a flower whose fill is 0.5
- **THEN** the response is `200` with that `flowerId` and `honey` greater than 0
- **AND** the flower's fill in the next level returned by `GET /api/level/{playerId}` is 0 (or has only grown back by its rate since)

#### Scenario: Harvesting an almost empty flower
- **WHEN** a client posts to `/api/harvest/{id}` for a flower whose fill is 0.1 or less
- **THEN** the response is `200` with `honey` equal to 0 and the flower's fill is unchanged

#### Scenario: Harvesting an unknown flower
- **WHEN** a client posts to `/api/harvest/{id}` with an id that matches no flower
- **THEN** the response is `200` with `honey` equal to 0

### Requirement: A successful harvest is broadcast to all clients
After a successful harvest (honey greater than 0), the backend SHALL publish exactly one SSE event
`{"type": "harvest", "flowerId": <id>, "fill": 0}` on `GET /api/events` to every connected client. A
harvest that yields no honey SHALL NOT publish a `harvest` event.

#### Scenario: Successful harvest publishes an event
- **WHEN** a harvest of a filled flower succeeds
- **THEN** every SSE subscriber receives `{"type":"harvest","flowerId":<id>,"fill":0}`

#### Scenario: Failed harvest publishes nothing
- **WHEN** a harvest yields 0 honey (empty or unknown flower)
- **THEN** no `harvest` event is published

### Requirement: Clients show a broadcast harvest immediately
When the frontend receives a `harvest` event, it SHALL set the flower identified by `flowerId` to
the event's `fill` and play the depleted animation, without waiting for the next `level-update`.

#### Scenario: Another player empties a flower
- **WHEN** player A harvests a filled flower while player B is connected
- **THEN** player B's view shows that flower as empty right after the event arrives, before the next `level-update`
