---
name: feedback_tests_before_push
description: CI does not run tests; before commit/push run locally the tests relevant to the change — not blindly every suite
metadata:
  type: feedback
---

Run tests locally before committing and pushing — the GitHub pipelines do not run them (the
backend builds with `-DskipTests`). Pick the tests that make sense for the change (commands in
[[reference_build_and_test]]): test classes of the touched code plus whatever depends on it;
for frontend changes a headless-browser run against a local backend.

**Why:** Gerald (presserl, 2026-09-26/27): tests take too long in CI; "tests immer nur die laufen
lassen, die auch Sinn ergeben. Nicht immer alle blind."

**How to apply:** Before any commit/push (e.g. the archive step of an OpenSpec change), state which
tests were chosen and why, run them and report the results; do not push with failing tests. Run the
full suite (`./mvnw verify`) only before a release or when Gerald asks.
