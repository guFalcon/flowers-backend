---
name: reference_ci_runners
description: Self-hosted GitHub runners on babylon5 (fast) and dev1 (slow VM); shared UnterrainerInformatik workflows used by the flowers pipelines (npm ci, Node 24 majors, deploy-workflow WireGuard-or-none); runner-too-old fix; dispatch ≠ new release
metadata:
  type: reference
---

Condensed from presserl's memory (2026-10-05); details there.

**Runners.** Two sets of org-scoped (`UnterrainerInformatik`) self-hosted runners
`ghar-runner1..3`, both registered with `linux,x64,ubuntu-latest`; a job without a narrower label
lands on either set at random.
- **babylon5** (`babylon5-runner1..3`, extra label `babylon5`): Ryzen 5 5600, bare metal, fast. Also
  hosts the guFalcon repo runners (`ghar-priv-*`). Compose in `babylon5:~/scripts/github-runner/`
  (git repo `scripts`; Gerald keeps uncommitted edits there — don't commit them).
- **dev1** (`dev1-runner1..3`): 2012 Xeon VM, 3–4× slower CPU, ~30× slower disk. Cannot reach some
  deploy hosts — pin a deploy to babylon5 with the `runs-on` input (JSON label array) of
  `deploy-workflow` / `docker-build-workflow` if it times out on SSH.

**flowers pipelines.** Both start with `bump-semver-workflow` on push to `main`.
- *Frontend* uses only shared workflows: `npm-build-workflow` → `docker-build-workflow` →
  `deploy-workflow`. `npm-build-workflow` installs with **`npm ci`** (since 2026-10-02), so the
  committed `package-lock.json` must be in sync with `package.json`; drift fails at `npm ci`.
- *Backend* has its own build job (`self-hosted` runner, checkout via `init-runner-action`,
  setup-java v6 with `cache: maven`, Maven wrapper `./mvnw package -DskipTests`, artifact
  `app-target` = `target/quarkus-app`) and its own docker job on `ubuntu-latest`, then
  `deploy-workflow`. On the Node 24 majors since housekeeping-pipeline-cleanups-images (2026-10-06).

**Node 24 action majors** (callers-node24-actions, 2026-10-02): checkout v7, cache v6,
setup-java v6, setup-node v7, upload/download-artifact v7/v8, docker qemu/buildx/login v4 +
build-push v7. `SpicyPizza/create-envfile` has no Node 24 release — other pipelines write
`./deploy/.env` in a shell step (`NAME=value`, log only key names). `init-runner-action` (first step
of every shared workflow) already checks out with v7.

**deploy-workflow** has no OpenVPN any more: the VPN is picked from secrets — `WG_CONFIG` →
WireGuard, else none. Passing `VPN_OVPN_FILE`/`VPN_USERNAME`/`VPN_PASSWORD` fails the caller at
parse time.

**Runner too old ⇒ jobs stay `queued` forever** while GitHub shows the runners idle (runner
downloads an update, exits, restarts from the old image, loops). Fix on babylon5:
`cd ~/scripts/github-runner && docker compose build --pull && docker compose up -d --force-recreate --remove-orphans`;
check with `docker logs <c> | grep 'Current runner version'`. dev1 (`ssh dev1`, needs VPN) the same.

**A dispatch does not release a new version.** `bump-semver-workflow` logs `No new commits since
previous tag. Skipping...` and reuses the current version — a `workflow_dispatch` redeploys the
same version; a new version needs a new commit.

See [[reference_build_and_test]] and [[project_flowers]].
