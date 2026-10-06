---
name: feedback_contract_both_repos
description: A change to a REST endpoint or SSE event updates backend and frontend in the same OpenSpec change
metadata:
  type: feedback
---

When a REST endpoint (route, method, params, request/response shape) or an SSE event (`type`,
payload fields) changes, update the frontend (`index.html` URL constants and handlers,
`sse-connection.js`, `bee.js`) in the same change, plus the `.http` files ([[feedback_http_tests]]).

**Why:** Carried over from presserl/java-overmind-server, where the endpoints primer and the client
had to move with the backend; drift meant a client built against a stale contract. Here the
frontend is the only client and both repos deploy independently on push, so a half-done contract
change breaks the live game.

**How to apply:** The contract is defined by `GameResource` and the payloads it publishes on the
event bus — no separate primer file. Push both repos together at archive time, backend first when
the change is backwards compatible, otherwise say what breaks in between. See
[[feedback_openspec_only_changes]] and [[project_flowers]].
