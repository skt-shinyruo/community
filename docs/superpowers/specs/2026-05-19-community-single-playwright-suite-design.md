# Community Single Playwright Suite Design

Date: 2026-05-19

## Status

Accepted for implementation. The user requested an independent folder named
`tests/playwright-single` for reusable local Playwright tests against the
deployed `single` topology.

## Context

The repository currently has frontend unit and component tests under
`frontend/src/**/*.test.js`, backend tests under `backend/**/src/test`, and
deployment shell tests under `deploy/tests`. Browser-based single topology
checks have so far been manual or ad hoc, with the latest manual report recorded
in `docs/handbook/single-playwright-test-report-2026-05-19.md`.

The new suite must be independent from `frontend` because it validates the
deployed product surface through the public frontend and gateway:

```text
Playwright browser
  -> frontend nginx at http://localhost:12881
  -> gateway at http://localhost:12880
  -> backend / IM / storage / infrastructure in single topology
```

## Goals

- Provide a reusable local E2E suite under `tests/playwright-single`.
- Keep it runnable with `npm --prefix tests/playwright-single ...`.
- Target an already running `single` deployment by default.
- Reuse fixed dev accounts: `aaa/aaa`, `bbb/aaa`, and `admin/aaa`.
- Persist Playwright artifacts, traces, and a Chinese Markdown report under the
  independent test folder.
- Separate passing product checks from known current failures so local runs can
  either smoke-check the environment or track unresolved issues explicitly.

## Non-Goals

- Do not move frontend Vitest tests into the new folder.
- Do not manage Docker compose lifecycle automatically in the first version.
- Do not reset databases, Redis, object storage, or Elasticsearch
  automatically in the first version.
- Do not perform destructive admin actions such as freezing wallets, rolling
  back transactions, changing user roles, or resolving disputes.
- Do not solve dynamic captcha flows for full registration/password reset in the
  first version.

## Directory Shape

```text
tests/playwright-single/
  package.json
  playwright.config.ts
  README.md
  .env.example
  fixtures/
    accounts.ts
    auth.ts
    helpers.ts
    test-data.ts
  scripts/
    health-check.mjs
    markdown-report.mjs
  tests/
    00-smoke.spec.ts
    01-auth.spec.ts
    02-community.spec.ts
    03-wallet.spec.ts
    04-market.spec.ts
    05-drive.spec.ts
    06-admin.spec.ts
    99-known-issues.spec.ts
  reports/
    .gitkeep
```

Generated artifacts such as `.auth`, `test-results`, and `playwright-report`
remain inside `tests/playwright-single` and should be gitignored.

## Runtime Configuration

Default URLs:

- `SINGLE_WEB_BASE_URL=http://localhost:12881`
- `SINGLE_API_BASE_URL=http://localhost:12880`

The suite reads optional overrides from environment variables. `.env.example`
documents the variables, but the first version does not require an `.env` file.

## Commands

From the repository root:

```bash
npm --prefix tests/playwright-single install
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test:known
npm --prefix tests/playwright-single run report
```

`test` excludes `99-known-issues.spec.ts` by default. `test:known` sets
`PW_INCLUDE_KNOWN_ISSUES=1` and runs that file explicitly.

The expected deployment prerequisite is:

```bash
./deploy/deployment.sh up --topology single --no-observability
```

## Test Organization

- `00-smoke.spec.ts`: frontend reachable, gateway health, anonymous posts page,
  login page, and protected route redirect.
- `01-auth.spec.ts`: login/logout for dev accounts, register/password-reset
  page rendering, and empty-form validation.
- `02-community.spec.ts`: post creation, detail view, like, bookmark action,
  comment, profile, social lists, notices, and settings page smoke.
- `03-wallet.spec.ts`: wallet page, recharge, and transfer using small amounts.
- `04-market.spec.ts`: publish a virtual preloaded listing, buyer order
  creation, buyer/seller order visibility, inventory add/invalidate, and address
  creation.
- `05-drive.spec.ts`: folder create/rename/delete and retained share link
  creation.
- `06-admin.spec.ts`: ordinary user forbidden route, admin menu visibility,
  analytics and moderation page loads, and non-destructive admin page rendering
  checks.
- `99-known-issues.spec.ts`: explicit assertions for current known failures:
  bookmarks 503, IM conversations 403, some admin body routes missing, dev body
  missing, and public drive share verification 503.

State-changing spec files use serial execution and unique names derived from a
test run id. The first version avoids cleanup because local single data may be
useful for debugging; later work can add a reset/cleanup mode once the reset
contract is agreed.

## Authentication

`fixtures/auth.ts` logs in through the UI and saves storage states for:

- `aaa`
- `bbb`
- `admin`

Tests reuse those states where possible. If a saved state expires, the helper
recreates it. This keeps tests readable while still exercising the real login
flow in setup.

## Reporting

Playwright produces HTML and JSON reports. `scripts/markdown-report.mjs` converts
the JSON report into a Chinese Markdown summary under
`tests/playwright-single/reports/`.

The report includes:

- run timestamp,
- target URLs,
- pass/fail/known issue counts,
- failed test names,
- links to Playwright artifact directories when available.

Long-term project summaries can still be copied or rewritten under
`docs/handbook`, but ordinary local run output stays inside
`tests/playwright-single/reports`.

## Error Handling

- Health check fails fast if frontend or gateway is unreachable.
- Known current product failures live in `99-known-issues.spec.ts` and are
  excluded from the default product regression run.
- Tests wait for visible UI text or network responses rather than arbitrary long
  sleeps.
- Each state-changing test embeds a run id in created names to avoid collisions.

## Verification

The implementation should be verified with:

```bash
npm --prefix tests/playwright-single install
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run report
git diff --check -- tests/playwright-single docs/superpowers/specs/2026-05-19-community-single-playwright-suite-design.md
```

Full `npm --prefix tests/playwright-single run test` can be run when the local
single deployment is healthy and the user is comfortable adding test data to the
local environment.
