## MODIFIED Requirements

### Requirement: A successful harvest is broadcast to all clients
After a successful harvest (honey greater than 0), the backend SHALL publish exactly one SSE event
`{"type": "harvest", "flowerId": <id>, "fill": 0, "beeId": <id>, "honey": <integer>}` on
`GET /api/events` to every connected client, where `beeId` is the id of the harvesting bee and
`honey` its honey after the harvest (equal to `total` of the harvest response). A harvest that yields
no honey SHALL NOT publish a `harvest` event.

#### Scenario: Successful harvest publishes an event
- **WHEN** bee `p1` with 100 honey harvests a filled flower and gains 250
- **THEN** every SSE subscriber receives `{"type":"harvest","flowerId":<id>,"fill":0,"beeId":"p1","honey":350}`

#### Scenario: Failed harvest publishes nothing
- **WHEN** a harvest yields 0 honey (empty or unknown flower)
- **THEN** no `harvest` event is published
