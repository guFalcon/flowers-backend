## Context

- `LevelService` holds `bees` (id → `Bee`) under `synchronized`; `cleanupInactiveBees` runs
  `@Scheduled(every = "10s")` and drops bees with `now - lastActive > INACTIVITY_TIMEOUT_MILLIS`
  (60 000). `addBeeIfAbsent` is the single place where bees are created — both `registerBee`
  (join) and `updateTarget` (steer) go through it.
- Frontend `harvest.js` handles `pointerdown`: it looks up `beeInstances.get(state.yourBeeId)` and
  returns early when missing. `bees.js#renderBees` destroys instances absent from a level, so after
  the removal level-update the own instance is gone for good. `level.js#init()` fetches
  `GET /api/level/{PLAYER_ID}` and renders everything — exactly the rejoin we need.

## Goals / Non-Goals

**Goals:** bee survives realistic classroom pauses; after a removal the next click brings the same
bee (name, colour, honey) back without reload.

**Non-Goals:** see proposal. No REST/SSE shape change, so no `.http` request shape change.

## Decisions

1. **Timeout becomes a config property `flowers.inactivity-timeout` (Duration, default `5m`),
   cleanup interval stays 10 s.** A property instead of a constant lets the headless check run
   `quarkus:dev` with e.g. `-Dflowers.inactivity-timeout=15s` instead of waiting 5 minutes; 10 s
   granularity is irrelevant against 5 minutes. Tests keep calling `cleanupInactiveBees(now)` with a
   synthetic `now`.

2. **Retired bees in a second map `retiredBees` (id → `Bee` snapshot) inside `LevelService`,
   guarded by the same lock.** `removeInactiveBees` moves each removed bee there, freezing it at
   `positionAt(now)` (x/y/target = that position, path = one keyframe). `addBeeIfAbsent` checks
   `bees` → `retiredBees` → create. Restoring sets path to a single keyframe at `now`, `lastActive =
   now`, keeps colour and honey, and keeps the name unless an active bee has it (then
   `BeeNames.pick`). The returned `BeeRegistration.created` stays `true` for a restore, so
   `registerBee` publishes a level-update as for a new bee.
   - Alternative: never remove, only hide idle bees from levels. Rejected — it changes what
     "removed" means for the leaderboard and every level copy, more invasive.
   - Alternative: frontend remembers its own honey/name. Rejected — the server is the authority
     for honey; a client could fake it.

3. **Restart resets `honey` of retired bees too** in `restartLevel`, alongside the active bees, so a
   returning bee does not carry last round's honey into a new round.

4. **Names of retired bees are not reserved.** New players may take a retired bee's name; the
   returning bee then gets a fresh name (spec: leaderboard). Reserving would exhaust the 48-name list
   quickly in classes that rotate devices.

5. **Frontend rejoin in the click handler.** When `beeInstances.get(state.yourBeeId)` is missing (or
   `state.yourBeeId` is null), the handler `await init()` (re-registers on the server and renders
   the level incl. the own bee), then continues with the normal setTarget flow for the clicked
   coordinates. A simple in-flight guard (a shared promise) prevents parallel rejoins on rapid
   clicks. The `.then` of setTarget looks up the own instance anew (`beeInstances.get(...)`) instead
   of using the instance captured at click time; the flight-id check moves to that instance.
   - Alternative: just send setTarget without an instance (server restores the bee anyway) and wait
     for the level-update to create the instance. Rejected — the response's path would have no
     instance to land on and the harvest timer would race the SSE event.

## Risks / Trade-offs

- [`retiredBees` grows by one entry per distinct player id for the backend's lifetime] → classroom
  scale (tens of ids, localStorage-stable per device); entries are tiny. Acceptable; no eviction.
- [Rejoin adds one extra round trip on the first click after a removal] → only happens after 5
  idle minutes; invisible in practice.
- [Old tab of the same player id in a second window] → both share the id; behaviour unchanged from
  today.

## Migration Plan

Deploy backend and frontend via the normal push-to-main pipelines; order does not matter (the
frontend fix works against the old backend as well, the backend change is invisible to old
frontends except for the longer timeout). Rollback = revert the commits.
