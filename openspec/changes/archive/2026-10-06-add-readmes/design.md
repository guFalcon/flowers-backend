## Context

See proposal.md – Why. The facts the READMEs document come from the current code:
`GameResource` (endpoints), `LevelService` (schedulers: fill every 1 s, cleanup every 10 s with a
60 s inactivity timeout, `publishLevel` every 3 s and after every bee change), `EventBusService`
(SSE fan-out), `application.properties` (port 8084, CORS origins), `index.html`/`bee.js`/
`sse-connection.js`/`audio-system.js`/`app.js` in the frontend, and both `deploy/` folders and
pipelines.

## Goals / Non-Goals

**Goals:**
- A newcomer can run backend and frontend locally from the READMEs alone.
- The REST/SSE contract is documented once, in the backend README, with exact shapes.

**Non-Goals:**
- Documenting the planned work (leaderboard, clouds, frontend restructure) beyond a pointer that
  the backlog lives in `ai/open-proposals.md`.

## Decisions

- **Contract documented in the backend README only.** The backend owns the contract; the frontend
  README gives a one-paragraph summary and links to the backend section. Alternative — duplicating
  the tables — drifts on the next contract change.
- **Diagrams live in the backend repo** (`docs/diagrams/`, the control centre), the frontend README
  embeds them via `https://github.com/guFalcon/flowers-backend/raw/main/docs/diagrams/<name>.svg`.
  Alternative — copying the SVGs into the frontend — means two sources to keep in sync. Trade-off:
  the frontend README shows the images only after the backend change is pushed; both are pushed at
  archive time, backend first.
- **Two diagrams:** a component diagram (Browser → Express static files; Browser ↔ Quarkus via
  REST + SSE; Traefik in front of both in production) and a sequence diagram for one round with
  two players, showing that state reaches other players only via SSE.
- **Rendering:** `curl -sS -L -X POST -H 'Content-Type: text/plain; charset=utf-8'
  --data-binary @x.puml https://plantuml.unterrainer.info/plantuml/svg -o x.svg`; each SVG is
  checked visually (rendered to PNG and viewed) before committing.
- **Known defects:** documented behaviour is the actual behaviour. Example: the `harvest` SSE event
  is described as "sent when a harvest yields no honey (see open proposal)", not as the intended
  design. Wording stays neutral and short; the README is not a bug tracker.
- **Commands are verified.** Every command shown in the READMEs (`./mvnw quarkus:dev`,
  `npm ci && npm start`, image build) is run once during apply; servers started are stopped again.

## Risks / Trade-offs

- [READMEs drift from code on the next change] → later changes that touch the contract or run
  instructions include a README task (no new rule needed; the backend README is the contract doc).
- [Embedded SVGs from another repo break if paths move] → paths are fixed under `docs/diagrams/`.
- [Push redeploys both live sites without functional change] → acceptable; it is a normal patch
  release, pushed outside lesson time if Gerald prefers.
