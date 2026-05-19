# tests/playwright-single

This folder contains reusable Playwright E2E tests for a locally deployed
`single` topology. It is intentionally independent from `frontend` because it
tests the deployed browser surface through frontend nginx and the gateway.

## Prerequisite

From the repository root:

```bash
./deploy/deployment.sh up --topology single --no-observability
```

Default targets:

- Frontend: `http://localhost:12881`
- Gateway: `http://localhost:12880`

## Install

```bash
npm --prefix tests/playwright-single install
npx --prefix tests/playwright-single playwright install chromium
```

## Run

```bash
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test
npm --prefix tests/playwright-single run test:known
npm --prefix tests/playwright-single run report
```

`test` excludes `99-known-issues.spec.ts` by default. Use `test:known` when you
want to check the current known product failures separately.

## Scope

- Smoke checks verify frontend, gateway, anonymous posts, protected route
  redirect, and login.
- Product checks cover community, wallet, market, drive, and admin read flows.
- `99-known-issues.spec.ts` tracks current local single failures without hiding
  them in normal product assertions.

State-changing tests create unique names using a timestamp. The first version
does not reset local data automatically.
