## REMOVED Requirements

### Requirement: Clients show the player's honey from the server
**Reason**: The separate honey display in the top-right corner is removed; the leaderboard shows the
own bee's honey (from the level and the `harvest` events) in its highlighted row.
**Migration**: Read the own honey from the highlighted leaderboard row. The leaderboard still keeps
no client-side score; it only shows server values.
