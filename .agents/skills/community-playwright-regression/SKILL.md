---
name: community-playwright-regression
description: Use when verifying the community app UI after frontend, backend, deployment, or release-candidate changes, or when a broad local Playwright regression is needed.
---

# Community Playwright Regression

## Overview

Use this skill for a broad browser regression of the real community app after a fresh local `single` deployment. It verifies user-visible behavior at `http://localhost:12881`, backend health and API traffic through `http://localhost:12880`, and evidence quality good enough to diagnose failures.

The detailed route, API, role, and assertion checklist lives in `references/community-feature-matrix.md`. Load that file before browser work and treat it as the source of truth.

## Execution Modes

- **Full regression**: Default mode. Redeploy `single`, run the complete matrix, and report every item as `PASS`, `FAIL`, `BLOCKED`, or `NO_UI`.
- **Targeted follow-up**: Use only when the user names a specific changed area. Still run preflight, route/service freshness checks, auth/session guard checks, and the full matrix section for the changed area.

## Workflow

1. Start from repository root.
2. Redeploy `single` unless the user explicitly says not to:

   ```bash
   ./deploy/deployment.sh up --topology single --no-observability --env-file deploy/.env.single.example -p community-single
   ```

3. Verify runtime readiness before browser work:
   - `docker ps` shows expected `community-single-*` containers.
   - Gateway health at `http://127.0.0.1:12880/actuator/health` is `UP`.
   - Frontend shell loads at `http://localhost:12881`.
4. Run the freshness gate:
   - Compare `frontend/src/router/index.js` route paths against the matrix route inventory.
   - Compare non-test files in `frontend/src/api/services` against the API/service coverage map.
   - Treat any unaccounted route or service as a coverage failure until it is tested or explicitly marked `NO_UI`.
5. Use Playwright against `http://localhost:12881`, not `127.0.0.1`, to avoid origin and cookie mismatches.
6. Start with a clean browser context and clear cookies, `localStorage`, and `sessionStorage` before login.
7. Use seeded users from the matrix. Exercise regular-user, second-user, admin, owner, non-owner, participant, and non-participant paths where the UI exposes them.
8. For every feature area, verify:
   - route renders nonblank UI;
   - expected API or WebSocket request succeeds;
   - one meaningful positive action works;
   - one relevant negative, validation, permission, or ownership path behaves correctly;
   - changed state is visible after refresh, route reopen, recipient login, or list/detail reload.
9. Use unique test data prefixes such as `pw-YYYYMMDDHHMMSS`. Prefer reversible actions; clean up run-specific data when the UI provides a safe rollback.
10. On failure, collect network requests, console errors, route/action/user role, response status/body when available, and a screenshot for blank or broken UI states.

## Pass And Fail Rules

- `PASS` requires visible UI evidence plus the expected network/status/state assertion.
- `401` and `403` are expected only for routes that are supposed to be protected.
- Unexpected `401`, `403`, `409`, `429`, `500`, `503`, failed WebSocket bootstrap, blank routed views, and missing persistence are failures.
- If a page should render but is empty after navigation, reload once before declaring it broken.
- Do not extract bearer tokens from browser storage or bypass privileged workflows by calling protected APIs directly.
- If a UI surface does not exist for a backend/API capability, mark `NO_UI` and name the frontend service/API that needs component or API-level coverage.

## Reporting

When finished, summarize:

- deployment command, health result, and base URL;
- accounts and role/ownership paths used;
- route/service freshness results;
- pass/fail by feature area;
- failing route, action, request, status, role, and diagnosis;
- console errors, network evidence, and screenshot paths;
- skipped, blocked, or `NO_UI` items with reasons;
- test data prefix and cleanup status.

Use the final report template in `references/community-feature-matrix.md`.
