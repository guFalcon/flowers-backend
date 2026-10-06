## MODIFIED Requirements

### Requirement: Clients show a broadcast harvest immediately
When the frontend receives a `harvest` event, it SHALL set the flower identified by `flowerId` to
the event's `fill` and play the depleted animation, without waiting for the next `level-update`.

When the player's own bee finishes a flight the player started, the frontend SHALL call
`POST /api/player/{playerId}/harvest` once, unless a newer flight has started in the meantime. It
SHALL NOT decide itself which flower is harvested. On the response it SHALL:
- if `gained` is greater than 0, play the harvest sound and show `total` as the player's honey;
- if `gained` is 0 and `flowerId` is not null (the bee is on a flower that yields nothing), play the
  bump feedback;
- if `flowerId` is null (the bee is not on any flower), play no sound and give no bump feedback.

If the harvest request fails (no `200` response), the frontend SHALL give no harvest or bump
feedback.

#### Scenario: Another player empties a flower
- **WHEN** player A harvests a filled flower while player B is connected
- **THEN** player B's view shows that flower as empty right after the event arrives, before the next `level-update`

#### Scenario: Own flight ends on a flower
- **WHEN** the player clicks a filled flower and the bee arrives there
- **THEN** the frontend posts to `/api/player/{playerId}/harvest`, plays the harvest sound and shows the `total` from the response as honey

#### Scenario: Own flight ends on an empty flower
- **WHEN** the player's bee arrives on a flower that another player has just emptied, and the harvest response has that `flowerId` and `gained` 0
- **THEN** the frontend plays the bump feedback and the shown honey stays the same

#### Scenario: Own flight ends on the bare meadow
- **WHEN** the player clicks a spot outside every flower and the bee arrives there, and the harvest response has `flowerId` null
- **THEN** the frontend plays neither the harvest sound nor the bump feedback

#### Scenario: Flight is redirected
- **WHEN** the player clicks a flower and clicks somewhere else before the bee arrives
- **THEN** only the second flight triggers a harvest request
