---
name: project_flowers
description: What flowers is, its two repos (backend = control centre, frontend driven from here), live URLs and how deploys happen
metadata:
  type: project
---

flowers is a small multiplayer browser game used for teaching at HTL: every player steers a bee
(click/tap sets a target), bees harvest honey from flowers, state changes are pushed to all
clients via SSE; an admin can restart the level (QR code panel for joining).

- **Backend** `~/source/htl/java/flowers-backend` (`guFalcon/flowers-backend`, `main`): Quarkus
  3.26, Java 21, in-memory state, port 8084. Endpoints in `GameResource`: `GET /api/level/{playerId}`,
  `POST /api/player/{id}/target`, `POST /api/player/{id}/harvest`, `GET /api/events` (SSE),
  `POST /api/admin/restart` (header `X-Admin-Token` = env `FLOWERS_ADMIN_TOKEN`, dev token
  `dev-admin-token`; live token in `ai/secrets/flowers-admin-token`, admin view
  `?admin=<token>`). Server simulates flights and owns each bee's honey. CORS origins in
  `application.properties`.
- **Frontend** `~/source/htl/js/flowers-frontend` (`guFalcon/flowers-frontend`, `main`): static
  HTML/CSS/JS as ES modules (entry `main.js`) + Express (`app.js`), Node 24. `SERVER` constant in `config.js` points at the live
  backend (a commented-out localhost line for dev).
- **Live:** `https://flowers.htl.dev` (frontend) and `https://flowers-backend.htl.dev`, both
  docker compose behind Traefik (network `proxy_default`). Every push to `main` bumps the version,
  builds the image `gufalcon/flowers-*:latest` and redeploys via the shared `deploy-workflow`.

**Why:** Gerald (2026-10-05): the frontend repo "wird auch mit dem backend-claude gesteuert" — one
Claude session in the backend repo owns both; memory, OpenSpec and backlog live only here. Setup
copied from presserl the same day.

**How to apply:** Plan and record frontend changes in this repo's OpenSpec changes; do not create
`openspec/` or `ai/` in the frontend repo. A push to either `main` changes a live site — see
[[feedback_push_shared_ci_without_asking]] and [[feedback_contract_both_repos]].
